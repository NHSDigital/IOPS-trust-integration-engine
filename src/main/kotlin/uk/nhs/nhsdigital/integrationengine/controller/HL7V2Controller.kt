package uk.nhs.nhsdigital.integrationengine.controller

import ca.uhn.fhir.context.FhirContext
import io.swagger.v3.oas.annotations.Operation

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.parameters.RequestBody
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.nhs.nhsdigital.integrationengine.util.FhirSystems
import java.text.SimpleDateFormat

@RestController
@RequestMapping("/HL7v2")
class HL7V2Controller(@Qualifier("R4") private val fhirContext: FhirContext) {
    val v2MediaType = "x-application/hl7-v2+er7"
    var sdf = SimpleDateFormat("yyyyMMddHHmm")

    @Operation(summary = "Convert HL7 v2.4 ITK Message into FHIR R4 Resource")
    @PostMapping(path = ["/\$convertFHIRR4"], consumes = ["x-application/hl7-v2+er7"]
    , produces = ["application/fhir+json"])
    @RequestBody(
        description = "HL7 v2 event to be processed. Message must be formatted according to the [HSCIC HL7 v2 Message Specification](https://github.com/NHSDigital/NHSDigital-FHIR-ImplementationGuide/blob/master/documents/HSCIC%20ITK%20HL7%20V2%20Message%20Specifications.pdf)",
        required = true,
        content = [ Content(mediaType = "x-application/hl7-v2+er7" ,
            examples = [
                ExampleObject(
                    name = "HL7 v2.4 ADT_A03 Discharge a Patient.",
                    value = "MSH|^~\\&|MATSYSTEM|RCB|PAS|RCB|201003311730||ADT^A03^ADT_A03|13403891320453338089|P|2.4|0|20100331173057|||GBR|UNICODE|EN||iTKv1.0\n" +
                            "EVN||201003311720|||111111111^Cortana^Emily^^Miss^^RCB55|201003311725\n" +
                            "PID|1||3333333333^^^NHS||SMITH^FREDRICA^J^^MRS^^L|SCHMIDT^HELGAR^Y|196512131515|2|||29 WEST AVENUE^BURYTHORPE^MALTON^NORTH YORKSHIRE^YO32 5TT^GBR^H||+441234567890||EN|M|C22|||||A|Berlin|N||GBR||DEU||||ED\n" +
                            "PD1|||MALTON GP PRACTICE^^Y06601|G5612908^Townley^Gregory^^^Dr^^^GMC\n" +
                            "PV1|61|O|RCB^MATWRD^Bed 3^RCB55|82|||C3456789^Darwin^Samuel^^^Dr^^^GMC||C3456789^Darwin^Samuel^^^Dr^^^GMC|500||||79|B6||C3456789^Darwin^Samuel^^^Dr^^^GMC|Pregnant|11554^^^VISITID|||||||||||||||||19||||||||201003301100|201003311715\n" +
                            "PV2|||Labour||||||||||||||||||||||2|||||||||||||C",
                    summary = "Discharge Notification")])])
    fun convertFHIR(@org.springframework.web.bind.annotation.RequestBody v2Message : String): String {
        return fhirContext.newJsonParser().encodeResourceToString(
            Encounter()
                .setServiceProvider(Reference().setIdentifier(Identifier().setSystem(FhirSystems.ODS_CODE).setValue("RCP")))
                .setClass_(Coding().setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode").setCode("AMB"))
                .setSubject(Reference().setIdentifier(Identifier().setSystem(FhirSystems.NHS_NUMBER).setValue("3333333333")))
                .setPeriod(
                    Period().setStart(sdf.parse("201003301100"))
                        .setEnd(sdf.parse("201003311715"))
                )
                .addType(CodeableConcept().addCoding(Coding()
                    .setSystem(FhirSystems.SNOMED_CT)
                    .setCode("58000006")
                    .setDisplay("Patient discharge")
                )))
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
        return "MSH|^~\\&|Main_HIS|XYZ_HOSPITAL|iFW|ABC_Lab|20160915003015||ACK|9B38584D|P|2.6.1|\n" +
                "MSA|AA|9B38584D|Everything was okay dokay!|"
    }
}
