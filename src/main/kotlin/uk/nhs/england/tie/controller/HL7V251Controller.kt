package uk.nhs.england.tie.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.HL7Exception
import ca.uhn.hl7v2.HapiContext
import ca.uhn.hl7v2.model.Segment
import ca.uhn.hl7v2.model.v251.message.ADT_A01
import ca.uhn.hl7v2.model.v251.message.ADT_A02
import ca.uhn.hl7v2.model.v251.message.ADT_A03
import ca.uhn.hl7v2.model.v251.message.ADT_A05
import ca.uhn.hl7v2.model.v251.segment.*
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

import uk.nhs.england.tie.awsProvider.AWSEncounter
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.transforms.v24.PD1toFHIRPractitionerRole
import uk.nhs.england.tie.transforms.v24.PIDtoFHIRPatient
import uk.nhs.england.tie.transforms.v24.PV1toFHIRAppointment
import uk.nhs.england.tie.transforms.v24.PV1toFHIREncounter
import uk.nhs.england.tie.util.FhirSystems
import java.text.SimpleDateFormat
import java.util.*


@RestController
@RequestMapping("/HL7V2/2.5.1")

class HL7V251Controller(@Qualifier("R4") private val fhirContext: FhirContext,
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
        var mcf = CanonicalModelClassFactory("2.5.1")
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
                    name = "HL7 v2.5.1 ORU_R01 Unsolicited transmission of an observation message",
                    value = "MSH|^~\\&|ACMELab^2.16.840.1.113883.2.1.8.1.5.999^ISO|CAV^7A4BV^L|cymru.nhs.uk^2.16.840.1.113883.2. 1.8.1.5.200^ISO|NHSWales^RQFW3^L|20190514102527+0200||ORU^R01^ORU_R01|5051095-201905141025|T|2.5.1 |||AL\n" +
                            "PID|||403281375^^^154^PI~5189214567^^^NHS^NH||Bloggs^Joe^^^Mr||20010328|M|||A B M U Health Board^One Talbot Gateway^Baglan^Neath port talbot^SA12 7BR|||||||||||||||||||||01 PV1||O||||||||CAR\n" +
                            "ORC|OR||||||||||||7A3C7MPAT^^^wales.nhs.uk&7A3&L,M,N^^^^^MH Pathology Dept, OBR|1||914694928301|B3051^HbA1c (IFCC traceable)|||201803091500|||^ABM: Angharad Shore||||201803091500|^^Dr Andar Gunneberg|^Gunneberg^Andar^^^Dr||||||201803091500|||C\n" +
                            "NTE|1||For monitoring known diabetic patients, please follow NICE guidelines. If not a known diabetic and the patient is asymptomatic, a second confirmatory sample is required within 2 weeks (WEDS Guidance). HbA1c is unreliable for diagnostic and monitoring purposes in the context of several conditions, including some haemoglobinopathies, abnormal haemoglobin levels, chronic renal failure, recent transfusion, pregnancy, or alcoholism.\n" +
                            "OBX|1|NM|B3553^HbA1c (IFCC traceable)||49|mmol/mol|<48|H|||C|||201803091500 OBR|2||914694928301|B0001^Full blood count|||201803091500|||^ABM: Carl Owen||||201803091500|^^Dr Andar Gunneberg|^Gunneberg^Andar^^^Dr||||||201803091500|||F TQ1|||||||201803091400|201803091500|S^^^^^^^^Urgent\n" +
                            "OBX|1|NM|B0300^White blood cell (WBC) count||3.5|x10\\S\\9/L|4.0-11.0|L|||F|||201803091500 OBX|2|NM|B0307^Haemoglobin (Hb)||200|g/L|130-180|H|||F|||201803091500\n" +
                            "OBX|3|NM|B0314^Platelet (PLT) count||500|x10\\S\\9/L|150-400|H|||F|||201803091500 OBX|4|NM|B0306^Red blood cell (RBC) count||6.00|x10\\S\\12/L|4.50-6.00|N|||F|||201803091500 OBX|5|NM|B0308^Haematocrit (Hct)||0.60|L/L|0.40-0.52|H|||F|||201803091500\n" +
                            "OBX|6|NM|B0309^Mean cell volume (MCV)||120|fL|80-100|H|||F|||201803091500\n" +
                            "OBX|7|NM|B0310^Mean cell haemoglobin (MCH)||34.0|pg|27.0-33.0|H|||F|||201803091500 SPM|1|^9146949283||BLOO^Blood^ACME|||||||||||||201803091400|201803091500\n",
                    summary = "Pathology Report")])])
    fun convertFHIR(@org.springframework.web.bind.annotation.RequestBody v2Message : String): String {
        var resource : Resource? = null
        try {
            resource = convertORU(v2Message)
        } catch (ex: HL7Exception) {

        }
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



    fun convertORU(message : String) : Resource? {
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
              //  zu1 = adt03.get("ZU1") as Segment
                encounterType = CodeableConcept().addCoding(Coding()
                    .setSystem(FhirSystems.SNOMED_CT)
                    .setCode("58000006")
                    .setDisplay("Patient discharge"))
            }
            if (v2message is ADT_A01) {
                pid = v2message.pid
                pv1 = v2message.pV1
                msh = v2message.msh
             //   zu1 = v2message.get("ZU1") as Segment

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
               // zu1 = v2message.get("ZU1") as Segment
            }
            if (pv1 != null && pid != null) {
                if (msh != null) {

                    if (msh.messageType.triggerEvent.value.equals("A04")) {
                        encounterType = CodeableConcept().addCoding(Coding()
                            .setSystem(FhirSystems.SNOMED_CT)
                            .setCode("11429006")
                            .setDisplay("Consultation"))
                    }
                    val encounter = pV1toFHIREncounter.transform(xpv1)
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
                    when (msh.messageType.triggerEvent.value) {
                        "A28" -> {
                            return patient
                        }
                        "A31" -> {
                            return patient
                        }
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
