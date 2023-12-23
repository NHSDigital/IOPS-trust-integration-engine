package uk.nhs.england.tie.controller

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.HL7Exception
import ca.uhn.hl7v2.HapiContext
import ca.uhn.hl7v2.model.Segment
import ca.uhn.hl7v2.model.v251.message.ADT_A01
import ca.uhn.hl7v2.model.v251.message.ADT_A02
import ca.uhn.hl7v2.model.v251.message.ADT_A03
import ca.uhn.hl7v2.model.v251.message.ADT_A05
import ca.uhn.hl7v2.model.v251.message.ORU_R01
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
import uk.nhs.england.tie.transforms.v251.*
import uk.nhs.england.tie.util.FhirSystems
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log


@RestController
@RequestMapping("/HL7/v2.5.1")
@io.swagger.v3.oas.annotations.tags.Tag(name="HL7 v2 Events - ADT", description =
"[NHS Digital ITK HL7 v2 Message Specification](" +
        "https://github.com/NHSDigital/NHSDigital-FHIR-ImplementationGuide/raw/master/documents/HSCIC%20ITK%20HL7%20V2%20Message%20Specifications.pdf) \n"
        + "[DHCW HL7 v2 ORU_R01](https://github.com/NHSDigital/IOPS-Frameworks/blob/main/documents/DHCW%20HL7%202.5%20ORUR01%20Specification%202.0.0.docx.pdf) \n"
        + "[IHE PIX](https://profiles.ihe.net/ITI/TF/Volume1/ch-5.html) \n"
)
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
    var orCtoFHIRDiagnosticReport = ORCtoFHIRDiagnosticReport()
    var obRtoFHIRDiagnosticReport = OBRtoFHIRDiagnosticReport()
    var obXtoFHIRObservation = OBXtoFHIRObservation()
    var ntEtoFHIRAnnotation = NTEtoFHIRAnnotation()


    init {
        var mcf = CanonicalModelClassFactory("2.5.1")
        context.setModelClassFactory(mcf)
    }

    companion object : KLogging()

    @Operation(summary = "Convert HL7 v2.5.1 (Digital Health and Care Wales) Message into FHIR R4 Resource")
    @PostMapping(path = ["/\$convertFHIRR4"], consumes = ["x-application/hl7-v2+er7"]
    , produces = ["application/fhir+json"])
    @RequestBody(
        description = "HL7 v2 event to be processed.",
                ///Message must be formatted according to the [HSCIC HL7 v2 Message Specification](https://github.com/NHSDigital/NHSDigital-FHIR-ImplementationGuide/blob/master/documents/HSCIC%20ITK%20HL7%20V2%20Message%20Specifications.pdf)",
        required = true,
        content = [ Content(mediaType = "x-application/hl7-v2+er7" ,
            examples = [
                ExampleObject(
                    name = "HL7 v2.5.1 ORU_R01 Pathology Result",
                    value = "MSH|^~\\&|ACMELab^2.16.840.1.113883.2.1.8.1.5.999^ISO|CAV^7A4BV^L|cymru.nhs.uk^2.16.840.1.113883.2. 1.8.1.5.200^ISO|NHSWales^RQFW3^L|20190514102527+0200||ORU^R01^ORU_R01|5051095-201905141025|T|2.5.1|||AL\n" +
                            "PID|||403281375^^^154^PI~5189214567^^^NHS^NH||Bloggs^Joe^^^Mr||20010328|M|||A B M U Health Board^One Talbot Gateway^Baglan^Neath port talbot^SA12 7BR|||||||||||||||||||||01\n" +
                            "PV1||O||||||||CAR\n" +
                            "ORC|OR||||||||||||7A3C7MPAT^^^wales.nhs.uk&7A3&L,M,N^^^^^MH Pathology Dept\n" +
                            "OBR|1||914694928301|B3051^HbA1c (IFCC traceable)|||201803091500|||^ABM: Angharad Shore||||201803091500|^^Dr Andar Gunneberg|^Gunneberg^Andar^^^Dr||||||201803091500|||C\n" +
                            "NTE|1||For monitoring known diabetic patients, please follow NICE guidelines. If not a known diabetic and the patient is asymptomatic, a second confirmatory sample is required within 2 weeks (WEDS Guidance). HbA1c is unreliable for diagnostic and monitoring purposes in the context of several conditions, including some haemoglobinopathies, abnormal haemoglobin levels, chronic renal failure, recent transfusion, pregnancy, or alcoholism.\n" +
                            "OBX|1|NM|B3553^HbA1c (IFCC traceable)||49|mmol/mol|<48|H|||C|||201803091500\n" +
                            "OBR|2||914694928301|B0001^Full blood count|||201803091500|||^ABM: Carl Owen||||201803091500|^^Dr Andar Gunneberg|^Gunneberg^Andar^^^Dr||||||201803091500|||F\n" +
                            "TQ1|||||||201803091400|201803091500|S^^^^^^^^Urgent\n" +
                            "OBX|1|NM|B0300^White blood cell (WBC) count||3.5|x10\\S\\9/L|4.0-11.0|L|||F|||201803091500\n" +
                            "OBX|2|NM|B0307^Haemoglobin (Hb)||200|g/L|130-180|H|||F|||201803091500\n" +
                            "OBX|3|NM|B0314^Platelet (PLT) count||500|x10\\S\\9/L|150-400|H|||F|||201803091500\n" +
                            "OBX|4|NM|B0306^Red blood cell (RBC) count||6.00|x10\\S\\12/L|4.50-6.00|N|||F|||201803091500\n" +
                            "OBX|5|NM|B0308^Haematocrit (Hct)||0.60|L/L|0.40-0.52|H|||F|||201803091500\n" +
                            "OBX|6|NM|B0309^Mean cell volume (MCV)||120|fL|80-100|H|||F|||201803091500\n" +
                            "OBX|7|NM|B0310^Mean cell haemoglobin (MCH)||34.0|pg|27.0-33.0|H|||F|||201803091500\n" +
                            "SPM|1|^9146949283||BLOO^Blood^ACME|||||||||||||201803091400|201803091500\n",
                    summary = "Pathology Result"),
                ExampleObject(
                    name = "HL7 v2.5.1 ORU_R01 Text based report",
                    value = "MSH|^~\\&|ACMELab^2.16.840.1.113883.2.1.8.1.5.999^ISO|CAV^7A4BV^L|cymru.nhs.uk^2.16.840.1.113883.2. 1.8.1.5.200^ISO|NHSWales^RQFW3^L|20190514102527+0200||ORU^R01^ORU_R01|5051095-201905141025|T|2.5.1|||AL\n" +
                            "PID|||403281375^^^154^PI~5189214567^^^NHS^NH||Bloggs^Joe^^^Mr||20010328|M|||A B M U Health Board^One Talbot Gateway^Baglan^Neath port talbot^SA12 7BR|||||||||||||||||||||01\n" +
                            "PV1||U||||||||CAR\n" +
                            "ORC|MC||||||||||||7A3C7MPAT^^^wales.nhs.uk&7A3&HL7^^^^^MH Pathology Dept,\n" +
                            "OBR|1||8005372251-1-M0007|M0007^Urine MC\\T\\S|||201805240000|||^ABM: Dean Allen||||201805240000|^^Dr J A Chess|^Chess^J^^^^Dr||||||201805240000|||F" +
                            "TQ1|||||||201805240000|201805240000|R^^^^^^^^Routine\n" +
                            "OBX|1|TX|^Report Line 1||Specimen received: Mid Stream Urine||||||F|||201805240000\n" +
                            "OBX|2|TX|^Report Line 2||||||||F|||201805240000\n" +
                            "OBX|3|TX|^Report Line 3||Accession Number(s) U18S999001A||||||F|||201805240000\n" +
                            "OBX|4|TX|^Report Line 4||White Blood Cell Count 10-99 x10\\S\\6/L||||||F|||201805240000\n" +
                            "OBX|5|TX|^Report Line 5||Red Blood Cell Count >=100 x10\\S\\6/L||||||F|||201805240000\n" +
                            "OBX|6|TX|^Report Line 6||||||||F|||201805240000\n" +
                            "OBX|7|TX|^Report Line 7||>= 10\\S\\8 cfu/L Escherichia coli (ECOL)||||||F|||201805240000\n" +
                            "OBX|8|TX|^Report Line 8||||||||F|||201805240000\n" +
                            "OBX|9|TX|^Report Line 9|| Antibiotic/Culture: ECOL||||||F|||201805240000\n" +
                            "OBX|10|TX|^Report Line 10|| ------------------- ------||||||F|||201805240000\n" +
                            "OBX|11|TX|^Report Line 11|| Nitrofurantoin\n" +
                            "OBX|12|TX|^Report Line 12|| Trimethoprim\n" +
                            "OBX|13|TX|^Report Line 13|| Amoxicillin\n" +
                            "OBX|14|TX|^Report Line 14||||||||F|||201805240000\n" +
                            "SPM|1|^8005372251||MMSU^ Mid Stream Urine^ACME|||||||||||||201805240000|201805240000",
                    summary = "Text based report")
            ])])
    fun convertFHIR(@org.springframework.web.bind.annotation.RequestBody v2Message : String): String {
        var resource : Resource? = null
        try {
            resource = convertORU(v2Message)
        } catch (ex: HL7Exception) {
            return fhirContext.newJsonParser().encodeResourceToString(OperationOutcome().addIssue(OperationOutcome.OperationOutcomeIssueComponent()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.PROCESSING)
                .setDiagnostics(ex.message)
            ))
        }
        catch (ex: Exception) {
            return fhirContext.newJsonParser().encodeResourceToString(OperationOutcome().addIssue(OperationOutcome.OperationOutcomeIssueComponent()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.PROCESSING)
                .setDiagnostics(ex.message)
            ))
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
        var patientFullUrl : String? = null
        var encounterFullUrl : String? = null

        val parser :PipeParser  = context.getPipeParser()
        parser.parserConfiguration.isValidating = false
        try {
            val v2message = parser.parse(message2)

            if (v2message != null) {
                logger.info(v2message.name)
                var patient : Patient? = null
                var encounter : Encounter? = null
                var placerRef: String? = null

                if (v2message is ORU_R01) {
                    pid = v2message.patienT_RESULT.patient.pid
                    pd1 = v2message.patienT_RESULT.patient.pD1
                    pv1 = v2message.patienT_RESULT.patient.visit.pV1
                    msh = v2message.msh
                   // zu1 = v2message.get("ZU1") as Segment
                }


                if (pv1 != null && pid != null) {
                    if (pv1 !== null) encounter = pV1toFHIREncounter.transform(pv1)
                    if (pid !== null) patient = piDtoFHIRPatient.transform(pid)
                    if (msh != null) {

                        if (msh.messageType.triggerEvent.value.equals("A04")) {
                            encounterType = CodeableConcept().addCoding(Coding()
                                .setSystem(FhirSystems.SNOMED_CT)
                                .setCode("11429006")
                                .setDisplay("Consultation"))
                        }

                        // Need to double check this is correct - does admit mean arrived`
                        if (encounter != null) {
                            encounter.status =
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
                            // Provider fix
                            if (encounter.hasIdentifier()) {
                                val odsCode = msh.sendingFacility.namespaceID.value
                                if (odsCode == null) {
                                    encounter.identifierFirstRep.setValue(pv1.visitNumber.idNumber.value).system =
                                        "http://terminology.hl7.org/CodeSystem/v2-0203"
                                } else {
                                    encounter.identifierFirstRep.setValue(pv1.visitNumber.idNumber.value).system =
                                        "https://fhir.nhs.uk/" + odsCode + "/Id/Encounter"
                                    encounter.serviceProvider = Reference().setIdentifier(
                                        Identifier()
                                            .setSystem(FhirSystems.ODS_CODE)
                                            .setValue(odsCode)
                                    )
                                }
                            }

                            if (encounterType != null) encounter.serviceType = encounterType
                            if (patient !== null)
                                for (identifier in patient.identifier) {
                                    if (identifier.system.equals(FhirSystems.NHS_NUMBER)) encounter.subject =
                                        Reference().setIdentifier(identifier)
                                }
                        }
                    }

                }

                if (pd1 !=null && patient !== null) {
                    var practitionerRole = pD1toFHIRPractitionerRole.transform(pd1)
                    if (practitionerRole != null) {
                        if (practitionerRole.hasPractitioner()) patient.addGeneralPractitioner(practitionerRole.practitioner)
                        if (practitionerRole.hasOrganization()) patient.addGeneralPractitioner(practitionerRole.organization)
                    }
                }

                var bundle = Bundle().setType(Bundle.BundleType.TRANSACTION)
                if (patient !== null) {
                    val uuid = UUID.randomUUID();
                    patientFullUrl = "urn:uuid:" + uuid.toString()
                    bundle.addEntry(
                        Bundle.BundleEntryComponent()
                            .setFullUrl(patientFullUrl)
                            .setResource(patient)
                            .setRequest(
                                Bundle.BundleEntryRequestComponent()
                                    .setMethod(Bundle.HTTPVerb.POST)
                                    .setUrl("Patient")
                            )
                    )
                }
                if (encounter !== null) {
                    val uuid = UUID.randomUUID();
                    encounterFullUrl = "urn:uuid:" + uuid.toString()
                    bundle.addEntry(
                        Bundle.BundleEntryComponent()
                            .setFullUrl(encounterFullUrl)
                            .setResource(encounter)
                            .setRequest(
                                Bundle.BundleEntryRequestComponent()
                                    .setMethod(Bundle.HTTPVerb.POST)
                                    .setUrl("Encounter")
                            )
                    )
                }
                if (v2message is ORU_R01) {
                    logger.info(v2message.patienT_RESULT.ordeR_OBSERVATIONReps.toString())
                    val result = v2message.patienT_RESULT.ordeR_OBSERVATION
                    var diagnosticReport: DiagnosticReport? = null;
                    for (i in 1..v2message.patienT_RESULT.ordeR_OBSERVATIONReps) {

                        val result = v2message.patienT_RESULT.getORDER_OBSERVATION(i-1)
                        logger.info(result.name)
                        var diagnosticReport: DiagnosticReport? = null;
                        if (result.orc !== null) {
                            diagnosticReport = orCtoFHIRDiagnosticReport.transform(result.orc)
                        }
                        if (result.obr !== null) {
                            diagnosticReport = obRtoFHIRDiagnosticReport.transform(result.obr, diagnosticReport)
                        }
                        if (diagnosticReport !== null) {
                            if (result.nte !== null) {
                                result.nteAll.forEach{
                                    val annotation = ntEtoFHIRAnnotation.transform(it)
                                    if (annotation !== null) {
                                        diagnosticReport.conclusion = annotation.text
                                    }
                                }
                            }
                            if (diagnosticReport.hasBasedOn() && diagnosticReport.basedOnFirstRep.hasIdentifier()) {
                                placerRef = diagnosticReport.basedOnFirstRep.identifier.value
                                diagnosticReport.addIdentifier(Identifier().setValue(placerRef + "-" + i))
                                if (encounter !== null && !encounter.hasIdentifier()) {
                                    encounter.addIdentifier(Identifier().setValue(placerRef))
                                }
                            }

                            diagnosticReport.subject.reference = patientFullUrl
                            diagnosticReport.encounter.reference = encounterFullUrl
                            diagnosticReport.status =DiagnosticReport.DiagnosticReportStatus.FINAL
                            val uuid = UUID.randomUUID();
                            bundle.addEntry(
                                Bundle.BundleEntryComponent()
                                    .setFullUrl("urn:uuid:" + uuid.toString())
                                    .setResource(diagnosticReport)
                                    .setRequest(
                                        Bundle.BundleEntryRequestComponent()
                                            .setMethod(Bundle.HTTPVerb.POST)
                                            .setUrl("DiagnosticReport")
                                    )
                            )
                        }
                        logger.info (result.observationReps.toString())
                        for (f in 1..result.observationReps) {
                            var obseravtionV2 = result.getOBSERVATION(f-1)
                            val observation = obXtoFHIRObservation.transform(obseravtionV2.obx)
                            if (observation !== null) {
                                observation.subject.reference = patientFullUrl
                                observation.encounter.reference = encounterFullUrl
                                observation.status = Observation.ObservationStatus.FINAL
                                if (placerRef !== null) observation.addIdentifier(Identifier().setValue(placerRef + "-" + i + "-" + f))
                                val uuid = UUID.randomUUID();
                                if (diagnosticReport !== null) {
                                    diagnosticReport.result.add(Reference().setReference("urn:uuid:" + uuid.toString()))
                                }
                                bundle.addEntry(
                                    Bundle.BundleEntryComponent()
                                        .setFullUrl("urn:uuid:" + uuid.toString())
                                        .setResource(observation)
                                        .setRequest(
                                            Bundle.BundleEntryRequestComponent()
                                                .setMethod(Bundle.HTTPVerb.POST)
                                                .setUrl("Observation")
                                        )
                                )
                            }
                        }
                    }
                }

                return bundle
            }
        }
        catch (ex : Exception) {
            logger.error(ex.message)
            throw UnprocessableEntityException(ex.message)
        }
        return null
    }
}
