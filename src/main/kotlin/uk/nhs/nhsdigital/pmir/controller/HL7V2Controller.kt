package uk.nhs.nhsdigital.pmir.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.HapiContext
import ca.uhn.hl7v2.model.v24.message.ADT_A01
import ca.uhn.hl7v2.model.v24.message.ADT_A02
import ca.uhn.hl7v2.model.v24.message.ADT_A03
import ca.uhn.hl7v2.model.v24.message.ADT_A05
import ca.uhn.hl7v2.model.v24.segment.*
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory
import ca.uhn.hl7v2.parser.PipeParser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import mu.KLogging
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import uk.nhs.nhsdigital.pmir.awsProvider.AWSEncounter
import uk.nhs.nhsdigital.pmir.awsProvider.AWSPatient
import uk.nhs.nhsdigital.pmir.transforms.PD1toFHIRPractitionerRole
import uk.nhs.nhsdigital.pmir.transforms.PIDtoFHIRPatient
import uk.nhs.nhsdigital.pmir.transforms.PV1toFHIREncounter
import uk.nhs.nhsdigital.pmir.util.FhirSystems
import java.text.SimpleDateFormat
import java.util.*


@RestController
@RequestMapping("/V2/ITK")
@io.swagger.v3.oas.annotations.tags.Tag(name="HL7 v2 Events - ADT", description =
"[NHS Digital ITK HL7 v2.4](https://github.com/NHSDigital/NHSDigital-FHIR-ImplementationGuide/blob/master/documents/HSCIC%20ITK%20HL7%20V2%20Message%20Specifications.pdf) \n"
+ "[IHE PIX](https://profiles.ihe.net/ITI/TF/Volume1/ch-5.html) \n"
)
class HL7V2Controller(@Qualifier("R4") private val fhirContext: FhirContext,
                     val awsPatient : AWSPatient,
                      val awsEncounter : AWSEncounter) {
    val v2MediaType = "x-application/hl7-v2+er7"
    var sdf = SimpleDateFormat("yyyyMMddHHmm")

    var timestampSS = SimpleDateFormat("yyyyMMddHHmmss")
    var timestamp = SimpleDateFormat("yyyyMMddHHmm")

    var context: HapiContext = DefaultHapiContext()

    var pV1toFHIREncounter = PV1toFHIREncounter();
    var piDtoFHIRPatient = PIDtoFHIRPatient();
    var pD1toFHIRPractitionerRole = PD1toFHIRPractitionerRole()


    init {
        var mcf = CanonicalModelClassFactory("2.4")
        context.setModelClassFactory(mcf)
    }

    companion object : KLogging()

    @Operation(summary = "Convert HL7 v2.4 ITK Message into FHIR R4 Resource")
    @PostMapping(path = ["/\$convertFHIRR4"], consumes = ["x-application/hl7-v2+er7"]
    , produces = ["application/fhir+json"])
    @RequestBody(
        description = "HL7 v2 event to be processed. Message must be formatted according to the [HSCIC HL7 v2 Message Specification](https://github.com/NHSDigital/NHSDigital-FHIR-ImplementationGuide/blob/master/documents/HSCIC%20ITK%20HL7%20V2%20Message%20Specifications.pdf)",
        required = true,
        content = [ Content(mediaType = "x-application/hl7-v2+er7" ,
            examples = [
                ExampleObject(
                    name = "HL7 v2.4 ADT_A01 Admit Inpatient",
                    value = "MSH|^~\\&|PAS|RCB|ROUTE|ROUTE|201010101418||ADT^A01^ADT_A01|1391320453338055|P|2.4|1|20101010141857|||GBR|UNICODE|EN||iTKv1.0\n" +
                            "EVN||201010101400|||111111111^Cortana^Emily^^^Miss^^RCB55|201010101400\n" +
                            "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196512131515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|||GBR||DEU\n" +
                            "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMP\n" +
                            "NK1|1|SMITH^ALBERT^J^^MR^^L|1|29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H|+441234567890||||||||||1|196311111513||||EN\n" +
                            "PV1|1|I|RCB^OBS1^BAY2-6^RCB55|13|||C3456789^Darwin^Samuel^^^Dr^^^GMC|G5612908^Townley^Gregory^^^Dr^^^GMP|C3456789^Darwin^Samuel^^^Dr^^^GMC|300||||19|||||2139^^^VISITID|||||||||||||||||||||||||201010201716\n" +
                            "PV2||||||||||||||||||||||||||||||||||||||C\n" +
                            "ZU1|201010071234|1|C|201010091300||500|||||||||201010081200|201010081156|02|Y|0\n" +
                            "ZU3|004|03|5|||||Normal|8b||1|1\n" +
                            "ZU4||201010081756|201010090000\n" +
                            "ZU8|Z|1|No",
                    summary = "Admission Notification"),
                ExampleObject(
                    name = "HL7 v2.4 ADT_A03 Discharge Patient (Inpatient or Outpatient)",
                    value = "MSH|^~\\&|MATSYSTEM|RCB|PAS|RCB|201003311730||ADT^A03^ADT_A03|13403891320453338089|P|2.4|0|20100331173057|||GBR|UNICODE|EN||iTKv1.0\n" +
                            "EVN||201003311720|||111111111^Cortana^Emily^^Miss^^RCB55|201003311725\n" +
                            "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196512131515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|N||GBR||DEU||||ED\n" +
                            "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMP\n" +
                            "PV1|61|O|RCB^MATWRD^Bed 3^RCB55|82|||C3456789^Darwin^Samuel^^^Dr^^^GMC||C3456789^Darwin^Samuel^^^Dr^^^GMC|500||||79|B6||C3456789^Darwin^Samuel^^^Dr^^^GMC|Pregnant|11554^^^VISITID|||||||||||||||||19||||||||201003301100|201003311715\n" +
                            "PV2|||Labour||||||||||||||||||||||2|||||||||||||C",
                    summary = "Discharge Notification"),
                ExampleObject(
                    name = "HL7 v2.4 ADT_A28 Create New Patient",
                    value = "MSH|^~\\&|PAS|RCB|ROUTE|ROUTE|201001021215||ADT^A28^ADT_A05|13403891320453338075|P|2.4|0|20100102121557|||GBR|UNICODE|EN||iTKv1.0\n" +
                            "EVN||201001021213|||111111111^Cortana^Emily^^Miss^^RCB55|201001021213\n" +
                            "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196513121515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|N||GBR||DEU||||ED\n" +
                            "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMP\n" +
                            "NK1|2|SMITH^FRANCESCA^^^MRS^^L|16|29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H|+441234567890||||||||||1|196311111513||||EN\n" +
                            "AL1|1|DA|Z88.5|5||199807011755\n" +
                            "ZU8|U|1|Yes|",
                    summary = "Create Patient"),
                ExampleObject(
                    name = "HL7 v2.4 ADT_A31 Update Patient Information",
                    value = "MSH|^~\\&|PAS|RCB|ROUTE|ROUTE|201001021236||ADT^A31^ADT_A05|134039113204538055|P|2.4|0|20100102123657|||GBR|UNICODE|EN||iTKv1.0\n" +
                            "EVN||201001021237|||111111111^Cortana^Emily^^Miss^^RCB55|201001021230\n" +
                            "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196513121515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|N||GBR||DEU||||ED\n" +
                            "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMP\n" +
                            "NK1|2|SMITH^FRANCESCA^^^MRS^^L|16|29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H|+441234567890||||||||||1|196311111513||||EN\n" +
                            "AL1|1|DA|Z88.5|5||199807011755\n" +
                            "AL1|2|DA|T63.0|7||199306050000\n" +
                            "ZU8|U|1|Yes",
                    summary = "Update Patient")])])
    fun convertFHIR(@org.springframework.web.bind.annotation.RequestBody v2Message : String): String {

        var resource = convertADT(v2Message)
        if (resource == null) return "" else
        return fhirContext.newJsonParser().encodeResourceToString(resource)

        /*return fhirContext.newJsonParser().encodeResourceToString(
            Encounter()
                .setServiceProvider(Reference().setIdentifier(Identifier().setSystem(FhirSystems.ODS_CODE).setValue("RCP")))
                .setClass_(Coding().setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode").setCode("AMB"))
                .setSubject(Reference().setIdentifier(Identifier().setSystem(FhirSystems.NHS_NUMBER).setValue("3333333333")))
                .setPeriod(
                    Period().setStart(sdf.parse("201003301100"))
                        .setEnd(sdf.parse("201003311715"))
                )
                .addType(
                )))*/
    }

    @Operation(summary = "Send a HL7 v2.4 ITK Message to a FHIR R4 Server")
    @PostMapping
        (path = ["/\$process-event"], consumes = ["x-application/hl7-v2+er7"], produces = ["x-application/hl7-v2+er7"])
    @RequestBody(
        description = "HL7 v2 event to be processed. Message must be formatted according to the [HSCIC HL7 v2 Message Specification](https://github.com/NHSDigital/NHSDigital-FHIR-ImplementationGuide/blob/master/documents/HSCIC%20ITK%20HL7%20V2%20Message%20Specifications.pdf)",
        required = true,
        content = [ Content(mediaType = "x-application/hl7-v2+er7" ,
            examples = [
                ExampleObject(
                    name = "HL7 v2.4 ADT_A28 Create New Patient",
                    value = "MSH|^~\\&|PAS|RCB|ROUTE|ROUTE|201001021215||ADT^A28^ADT_A05|13403891320453338075|P|2.4|0|20100102121557|||GBR|UNICODE|EN||iTKv1.0\n" +
                            "EVN||201001021213|||111111111^Cortana^Emily^^Miss^^RCB55|201001021213\n" +
                            "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196513121515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|N||GBR||DEU||||ED\n" +
                            "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMP\n" +
                            "NK1|2|SMITH^FRANCESCA^^^MRS^^L|16|29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H|+441234567890||||||||||1|196311111513||||EN\n" +
                            "AL1|1|DA|Z88.5|5||199807011755\n" +
                            "ZU8|U|1|Yes|",
                    summary = "Create Patient"),
                ExampleObject(
                    name = "HL7 v2.4 ADT_A01 Admit Inpatient",
                    value = "MSH|^~\\&|PAS|RCB|ROUTE|ROUTE|201010101418||ADT^A01^ADT_A01|1391320453338055|P|2.4|1|20101010141857|||GBR|UNICODE|EN||iTKv1.0\n" +
                            "EVN||201010101400|||111111111^Cortana^Emily^^^Miss^^RCB55|201010101400\n" +
                            "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196512131515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|||GBR||DEU\n" +
                            "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMP\n" +
                            "NK1|1|SMITH^ALBERT^J^^MR^^L|1|29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H|+441234567890||||||||||1|196311111513||||EN\n" +
                            "PV1|1|I|RCB^OBS1^BAY2-6^RCB55|13|||C3456789^Darwin^Samuel^^^Dr^^^GMC|G5612908^Townley^Gregory^^^Dr^^^GMP|C3456789^Darwin^Samuel^^^Dr^^^GMC|300||||19|||||2139^^^VISITID|||||||||||||||||||||||||201010201716\n" +
                            "PV2||||||||||||||||||||||||||||||||||||||C\n" +
                            "ZU1|201010071234|1|C|201010091300||500|||||||||201010081200|201010081156|02|Y|0\n" +
                            "ZU3|004|03|5|||||Normal|8b||1|1\n" +
                            "ZU4||201010081756|201010090000\n" +
                            "ZU8|Z|1|No",
                    summary = "Admission Notification"),
            ExampleObject(
                name = "HL7 v2.4 ADT_A03 Discharge a Patient.",
                value = "MSH|^~\\&|MATSYSTEM|RCB|PAS|RCB|201003311730||ADT^A03^ADT_A03|13403891320453338089|P|2.4|0|20100331173057|||GBR|UNICODE|EN||iTKv1.0\n" +
                        "EVN||201003311720|||111111111^Cortana^Emily^^Miss^^RCB55|201003311725\n" +
                        "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196512131515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|N||GBR||DEU||||ED\n" +
                        "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMC\n" +
                        "PV1|61|O|RCB^MATWRD^Bed 3^RCB55|82|||C3456789^Darwin^Samuel^^^Dr^^^GMC||C3456789^Darwin^Samuel^^^Dr^^^GMC|500||||79|B6||C3456789^Darwin^Samuel^^^Dr^^^GMC|Pregnant|11554^^^VISITID|||||||||||||||||19||||||||201003301100|201003311715\n" +
                        "PV2|||Labour||||||||||||||||||||||2|||||||||||||C",
                summary = "Discharge Notification")])])
    fun processEvent(
        @org.springframework.web.bind.annotation.RequestBody v2Message : String): String {
        var resource = convertADT(v2Message)
        var newResource : Resource? = null
        if (resource is Patient) {
            newResource = awsPatient.createUpdateAWSPatient(resource, null) as Resource
        } else if (resource is Encounter) {
            newResource = awsEncounter.createUpdateAWSEncounter(resource) as Resource
        }
        if (newResource != null) {
            if (newResource.idElement != null) return "MSH|^~\\&|TIE|NHS_TRUST|PAS|RCB|"+timestamp.format(Date())+"||ACK|9B38584D|P|2.4|0|"+timestampSS.format(Date())+"|||GBR|UNICODE|EN||iTKv1.0\n" +
                    "MSA|AA|9B38584D|"+ newResource.idElement.value +"|"
        }
        return "MSH|^~\\&|TIE|NHS_TRUST|PAS|RCB|20160915003015||ACK|9B38584D|P|2.6.1|\n" +
                "MSA|AA|9B38584D|Everything was okay dokay!|"
    }

    fun convertADT(message : String) : Resource? {
        var pid: PID? = null
        val evn: EVN? = null
        var msh: MSH? = null
        var pd1: PD1? = null
        var nk1: List<NK1>? = null
        var pv1: PV1? = null
        var dg1: List<DG1>? = null
        var encounterType : CodeableConcept? = null;

        var message2 = message.replace("\n","\r")

        var parser :PipeParser  = context.getPipeParser();
        parser.parserConfiguration.isValidating = false
        val v2message = parser.parse(message2)

        if (v2message != null) {
            logger.info(v2message.name)

            if (v2message is ADT_A03) {
                val adt03: ADT_A03 = v2message
                pid = adt03.pid
                pv1 = adt03.pV1
                encounterType = CodeableConcept().addCoding(Coding()
                    .setSystem(FhirSystems.SNOMED_CT)
                    .setCode("58000006")
                    .setDisplay("Patient discharge"))
            }
            if (v2message is ADT_A01) {
                pid = v2message.pid
                pv1 = v2message.pV1

                encounterType = CodeableConcept().addCoding(Coding()
                    .setSystem(FhirSystems.SNOMED_CT)
                    .setCode("32485007")
                    .setDisplay("Hospital admission"))
            }
            if (v2message is ADT_A02) {
                pid = v2message.pid
                pv1 = v2message.pV1
                encounterType = CodeableConcept().addCoding(Coding()
                    .setSystem(FhirSystems.SNOMED_CT)
                    .setCode("107724000")
                    .setDisplay("Patient transfer"))
            }
            if (v2message is ADT_A05) {
                pid = v2message.pid
                pd1 = v2message.pD1
            }
            if (pv1 != null && pid != null) {
                var encounter = pV1toFHIREncounter.transform(pv1)
                // Need to double check this is correct - does admit mean arrived`
                if (v2message is ADT_A01 && encounter != null) encounter.status = Encounter.EncounterStatus.ARRIVED
                var patient = piDtoFHIRPatient.transform(pid)
                if (encounterType != null) encounter.serviceType = encounterType
                for ( identifier in patient.identifier) {
                    if (identifier.system.equals(FhirSystems.NHS_NUMBER)) encounter.subject = Reference().setIdentifier(identifier)
                }
                return encounter

            } else if (pid != null) {
                var patient = piDtoFHIRPatient.transform(pid)
                if (pd1 !=null) {
                    var practitionerRole = pD1toFHIRPractitionerRole.transform(pd1)
                    if (practitionerRole != null) {
                        if (practitionerRole.hasPractitioner()) patient.addGeneralPractitioner(practitionerRole.practitioner)
                        if (practitionerRole.hasOrganization()) patient.addGeneralPractitioner(practitionerRole.organization)
                    }
                }
                return patient
            }
        }
        return null
    }
}
