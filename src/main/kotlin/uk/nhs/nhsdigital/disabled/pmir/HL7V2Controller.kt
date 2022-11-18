package uk.nhs.nhsdigital.disabled.pmir

import ca.uhn.fhir.context.FhirContext
import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.HapiContext
import ca.uhn.hl7v2.model.Segment
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
import uk.nhs.nhsdigital.pmir.transforms.PV1toFHIRAppointment
import uk.nhs.nhsdigital.pmir.transforms.PV1toFHIREncounter
import uk.nhs.nhsdigital.pmir.util.FhirSystems
import java.text.SimpleDateFormat
import java.util.*


@RestController
@RequestMapping("/V2/ITK")
@io.swagger.v3.oas.annotations.tags.Tag(name="ITK HL7 v2 Events - ADT", description =
"[NHS Digital ITK HL7 v2 Message Specification](" +
        "https://github.com/NHSDigital/NHSDigital-FHIR-ImplementationGuide/raw/master/documents/HSCIC%20ITK%20HL7%20V2%20Message%20Specifications.pdf) \n"
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
    var pV1toFHIRAppointment = PV1toFHIRAppointment();
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
        description = "HL7 v2 event to be processed.",
                ///Message must be formatted according to the [HSCIC HL7 v2 Message Specification](https://github.com/NHSDigital/NHSDigital-FHIR-ImplementationGuide/blob/master/documents/HSCIC%20ITK%20HL7%20V2%20Message%20Specifications.pdf)",
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
                        name = "HL7 v2.4 ADT_A31 Update Patient",
                        value = "MSH|^~\\&|PAS|RCB|ROUTE|ROUTE|201001021236||ADT^A31^ADT_A05|134039113204538055|P|2.4|0|20100102123657|||GBR|UNICODE|EN||iTKv1.0\n" +
                                "EVN||201001021237|||111111111^Cortana^Emily^^Miss^^RCB55|201001021230\n" +
                                "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196513121515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|N||GBR||DEU||||ED\n" +
                                "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMC\n" +
                                "NK1|2|SMITH^FRANCESCA^^^MRS^^L|16|29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H|+441234567890||||||||||1|196311111513||||EN\n" +
                                "AL1|1|DA|Z88.5|5||199807011755\n" +
                                "AL1|2|DA|T63.0|7||199306050000\n" +
                                "ZU8|U|1|Yes",
                        summary = "Update Patient"),
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
                        summary = "Discharge Notification"),
                    ExampleObject(
                        name = "HL7 v2.4 ADT_A04 Register Outpatient Update.",
                        value = "MSH|^~\\&|KIOSK|RCB|PAS|RCB|201011011512||ADT^A04^ADT_A01|14038913245354|P|2.4||201011011512|||GBR|UNICODE|EN||iTKv1.0\n" +
                                "EVN||201011011512|||111111111^Cortana^Emily^^Miss^^RCB55|201001111512\n" +
                                "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196512131515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|||GBR||DEU\n" +
                                "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMC\n" +
                                "NK1|2|SMITH^FRANCESCA^^^MRS^^L|16|29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H|+441234567890||||||||||1|196311111513||||EN\n" +
                                "PV1|57|O|West Wing^RCB-AWRD^BED2^RCB55|13|||C3456789^Darwin^Samuel^^^Dr^^^GMC|G5612908^Townley^Gregory^^^Dr^^^GMC|C3456789^Darwin^Samuel^^^Dr^^^GMC|300||||19|||C3456789^Darwin^Samuel^^^Dr^^^GMC|OUTPATIENT|2141^^^VISITID|||||||||||||||||||||||||201011011600\n" +
                                "AL1|1|DA|Z88.5|5||199807011755\n" +
                                "PR1|56||U19.2^24 hour ambulatory electrocardiography^OPCS4||201011011512|D|1440|||||C3456789^Darwin^Samuel^^^Dr^^^GMC|C3\n" +
                                "ZU1||2|C|201011011530||300||||1|||GP|2|201011011624|201011011620|02|Y|0",
                        summary = "Register Outpatient Update"),
                    ExampleObject(
                        name = "HL7 v2.4 ADT_A05 Pre Admit Patient",
                        value = "MSH|^~\\&|PAS|RCB|ROUTE|ROUTE|201011011512||ADT^A05^ADT_A05|14038913245354|P|2.4|A05|201011011512|||GBR|UNICODE|EN||iTKv1.0\n" +
                                "EVN||201011011512|||111111111^Cortana^Emily^^Miss^^RCB55|201001111512\n" +
                                "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196512131515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|||GBR||DEU\n" +
                                "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMC\n" +
                                "NK1|1|SMITH^FRANCESCA^^^MRS^^L|16|29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H|+441234567890||||||||||1|196311111513||||EN\n" +
                                "PV1|1|P|West Wing^RCB-AWRD^BED2^RCB55|13|||C3456789^Darwin^Samuel^^^Dr^^^GMC|G5612908^Townley^Gregory^^^Dr^^^GMC|C3456789^Darwin^Samuel^^^Dr^^^GMC|300||||19|||C3456789^Darwin^Samuel^^^Dr^^^GMC|PREADMIT|2131^^^VISITID|||||||||||||||||||||||||201011011600\n" +
                                "AL1|1|DA|Z88.5|5||199807011755\n" +
                                "ZU1||2|C|201011011530||300||||1|||GP|2|201011011624|201011011620|02|Y|0",
                        summary = "Pre Admit Patient")])])
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
        description = "HL7 v2 event to be processed.",
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
                    name = "HL7 v2.4 ADT_A31 Update Patient",
                    value = "MSH|^~\\&|PAS|RCB|ROUTE|ROUTE|201001021236||ADT^A31^ADT_A05|134039113204538055|P|2.4|0|20100102123657|||GBR|UNICODE|EN||iTKv1.0\n" +
                            "EVN||201001021237|||111111111^Cortana^Emily^^Miss^^RCB55|201001021230\n" +
                            "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196513121515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|N||GBR||DEU||||ED\n" +
                            "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMC\n" +
                            "NK1|2|SMITH^FRANCESCA^^^MRS^^L|16|29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H|+441234567890||||||||||1|196311111513||||EN\n" +
                            "AL1|1|DA|Z88.5|5||199807011755\n" +
                            "AL1|2|DA|T63.0|7||199306050000\n" +
                            "ZU8|U|1|Yes",
                    summary = "Update Patient"),
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
                summary = "Discharge Notification"),
            ExampleObject(
                    name = "HL7 v2.4 ADT_A04 Register Outpatient Update.",
                value = "MSH|^~\\&|KIOSK|RCB|PAS|RCB|201011011512||ADT^A04^ADT_A01|14038913245354|P|2.4||201011011512|||GBR|UNICODE|EN||iTKv1.0\n" +
                        "EVN||201011011512|||111111111^Cortana^Emily^^Miss^^RCB55|201001111512\n" +
                        "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196512131515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|||GBR||DEU\n" +
                        "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMC\n" +
                        "NK1|2|SMITH^FRANCESCA^^^MRS^^L|16|29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H|+441234567890||||||||||1|196311111513||||EN\n" +
                        "PV1|57|O|West Wing^RCB-AWRD^BED2^RCB55|13|||C3456789^Darwin^Samuel^^^Dr^^^GMC|G5612908^Townley^Gregory^^^Dr^^^GMC|C3456789^Darwin^Samuel^^^Dr^^^GMC|300||||19|||C3456789^Darwin^Samuel^^^Dr^^^GMC|OUTPATIENT|2141^^^VISITID|||||||||||||||||||||||||201011011600\n" +
                        "AL1|1|DA|Z88.5|5||199807011755\n" +
                        "PR1|56||U19.2^24 hour ambulatory electrocardiography^OPCS4||201011011512|D|1440|||||C3456789^Darwin^Samuel^^^Dr^^^GMC|C3\n" +
                        "ZU1||2|C|201011011530||300||||1|||GP|2|201011011624|201011011620|02|Y|0",
                summary = "Register Outpatient Update"),
            ExampleObject(
                    name = "HL7 v2.4 ADT_A05 Pre Admit Patient",
                value = "MSH|^~\\&|PAS|RCB|ROUTE|ROUTE|201011011512||ADT^A05^ADT_A05|14038913245354|P|2.4|A05|201011011512|||GBR|UNICODE|EN||iTKv1.0\n" +
                        "EVN||201011011512|||111111111^Cortana^Emily^^Miss^^RCB55|201001111512\n" +
                        "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196512131515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|||GBR||DEU\n" +
                        "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMC\n" +
                        "NK1|1|SMITH^FRANCESCA^^^MRS^^L|16|29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H|+441234567890||||||||||1|196311111513||||EN\n" +
                        "PV1|1|P|West Wing^RCB-AWRD^BED2^RCB55|13|||C3456789^Darwin^Samuel^^^Dr^^^GMC|G5612908^Townley^Gregory^^^Dr^^^GMC|C3456789^Darwin^Samuel^^^Dr^^^GMC|300||||19|||C3456789^Darwin^Samuel^^^Dr^^^GMC|PREADMIT|2131^^^VISITID|||||||||||||||||||||||||201011011600\n" +
                        "AL1|1|DA|Z88.5|5||199807011755\n" +
                        "ZU1||2|C|201011011530||300||||1|||GP|2|201011011624|201011011620|02|Y|0",
                summary = "Pre Admit Patient")
            ])])
    fun processEvent(
        @org.springframework.web.bind.annotation.RequestBody v2Message : String): String {
        val resource = convertADT(v2Message)
        var newResource : Resource? = null
        if (resource is Patient) {
            newResource = awsPatient.createUpdate(resource, null) as Resource
        } else if (resource is Encounter) {
            newResource = awsEncounter.createUpdate(resource) as Resource
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

        var zu1: Segment? = null

        val message2 = message.replace("\n","\r")

        val parser :PipeParser  = context.getPipeParser()
        parser.parserConfiguration.isValidating = false
        val v2message = parser.parse(message2)

        if (v2message != null) {
            logger.info(v2message.name)

            if (v2message is ADT_A03) {
                val adt03: ADT_A03 = v2message
                pid = adt03.pid
                pv1 = adt03.pV1
                msh= adt03.msh
                zu1 = adt03.get("ZU1") as Segment
                encounterType = CodeableConcept().addCoding(Coding()
                    .setSystem(FhirSystems.SNOMED_CT)
                    .setCode("58000006")
                    .setDisplay("Patient discharge"))
            }
            if (v2message is ADT_A01) {
                pid = v2message.pid
                pv1 = v2message.pV1
                msh = v2message.msh
                zu1 = v2message.get("ZU1") as Segment

                encounterType = CodeableConcept().addCoding(Coding()
                    .setSystem(FhirSystems.SNOMED_CT)
                    .setCode("32485007")
                    .setDisplay("Hospital admission"))
            }
            if (v2message is ADT_A02) {
                pid = v2message.pid
                pv1 = v2message.pV1
                msh = v2message.msh
                zu1 = v2message.get("ZU1") as Segment

                encounterType = CodeableConcept().addCoding(Coding()
                    .setSystem(FhirSystems.SNOMED_CT)
                    .setCode("107724000")
                    .setDisplay("Patient transfer"))
            }
            if (v2message is ADT_A05) {
                pid = v2message.pid
                pd1 = v2message.pD1
                pv1 = v2message.pV1
                msh = v2message.msh
                zu1 = v2message.get("ZU1") as Segment
            }
            if (pv1 != null && pid != null) {
                if (msh != null) {

                    if (msh.messageType.triggerEvent.value.equals("A04")) {
                        encounterType = CodeableConcept().addCoding(Coding()
                            .setSystem(FhirSystems.SNOMED_CT)
                            .setCode("11429006")
                            .setDisplay("Consultation"))
                    }
                    val encounter = pV1toFHIREncounter.transform(pv1)
                    // Need to double check this is correct - does admit mean arrived`
                    if (v2message is ADT_A01 && encounter != null) encounter.status =
                        Encounter.EncounterStatus.INPROGRESS
                    when (msh.messageType.triggerEvent.value) {
                        "A01" -> {
                            encounter.status =
                                Encounter.EncounterStatus.INPROGRESS
                        }
                        "A03" -> {
                            encounter.status =
                                Encounter.EncounterStatus.FINISHED
                        }
                        "A05" -> {
                            encounter.status =
                                Encounter.EncounterStatus.PLANNED
                        }
                    }
                    if (zu1 != null) {
                      //  System.out.println(zu1.name)
                    }
                    // Provider fix
                    if (encounter.hasIdentifier()) {
                        val odsCode = msh.sendingFacility.namespaceID.value
                        if (odsCode == null) {
                            encounter.identifierFirstRep.setValue(pv1.visitNumber.id.value).system =
                                "http://terminology.hl7.org/CodeSystem/v2-0203"
                        } else {
                            encounter.identifierFirstRep.setValue(pv1.visitNumber.id.value).system =
                                "https://fhir.nhs.uk/" + odsCode + "/Id/Encounter"
                            encounter.serviceProvider = Reference().setIdentifier(
                                Identifier()
                                    .setSystem(FhirSystems.ODS_CODE)
                                    .setValue(odsCode)
                            )
                        }
                    }

                    var patient = piDtoFHIRPatient.transform(pid)
                    if (encounterType != null) encounter.serviceType = encounterType
                    for (identifier in patient.identifier) {
                        if (identifier.system.equals(FhirSystems.NHS_NUMBER)) encounter.subject =
                            Reference().setIdentifier(identifier)
                    }
                    return encounter
                }

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
