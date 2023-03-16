package uk.nhs.england.tie.configuration

import ca.uhn.fhir.context.FhirContext
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType

import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.nhs.england.tie.util.FHIRExamples


@Configuration
class OpenApiConfig(@Qualifier("R4") val ctx : FhirContext) {

    var PIX = "Patient Demographics Events"
    var ADT = "Admission and Discharge Events (ADT)"
    var BUNDLE = "Batch Record Transfer"
    var MHD = "Documents"
    var FORMS = "Structured Data Capture"
    var APIM = "Security and API Management"
    var WORKFLOW = "Workflow"
    var CARE = "Care Team, Episodes and Plans"
    var COMMUNICATION = "Care Communication"
    @Bean
    fun customOpenAPI(
        fhirServerProperties: FHIRServerProperties,
       // restfulServer: FHIRR4RestfulServer
    ): OpenAPI? {

        val englandFHIRMessage =  "\n\n ## NHS England FHIR Messages" +
         "\n\n | API | FHIR R4 | FHIR STU3 | FHIR Document | MESH | http | Notes | " +
         "\n |---|---|---|---|---|---|---|" +
         "\n | [Booking and Referral - FHIR API](https://digital.nhs.uk/developer/api-catalogue/booking-and-referral-fhir) | X | - | - | - | X | See `Process a message` <ul><li>servicerequest-request</li><li>servicerequest-response</li><li>booking-request</li><li>booking-response</li></ul> |" +
         "\n | [Electronic Prescription Service - FHIR API](https://digital.nhs.uk/developer/api-catalogue/electronic-prescription-service-fhir) | X | - | - |  - | X | See `Create a new prescription - Prescribing` and `Mark a prescription as dispensed - Dispensing` <ul><li>prescription-order</li><li>dispense-notification</li></ul> | " +
         "\n | [Virtual Wards](https://github.com/nhsengland/virtual-wards-draft-standards/blob/main/4_Data_Transfer_Mechanisms.md) | X | - | - | X | - |Work in progress. Expected to be a FHIR Message | " +
         "\n | [Genomics](https://simplifier.net/guide/fhir-genomics-implementation-guide?version=current) | X | - | - | - | X |Work in progress. <ul><li>genomictestrequest</li></ul> | " +
         "\n | [National Event Management Service - FHIR API](https://digital.nhs.uk/developer/api-catalogue/national-events-management-service-fhir) | - | X | - | - | X | This is sub divided into several individual message types. <ul><li>Blood Spot Test Outcome</li> <li>Newborn Hearing</li> <li>NIPE Outcome</li> <li>PDS Birth Notification</li> <li>PDS Change of Address</li> <li>PDS Change of GP</li> <li>PDS Death Notification</li> <li>PDS Record Change</li> <li>Professional Contacts</li> <li>Vaccinations</li> </ul> | " +
         "\n | [GP Connect Send Document - FHIR](https://digital.nhs.uk/developer/api-catalogue/gp-connect-send-document-fhir) | - | X | - | X | - | Limited to online consultation providers to GP Systems | " +
         "\n | [Transfer of Care Emergency Care Discharge - FHIR](https://digital.nhs.uk/developer/api-catalogue/transfer-of-care-emergency-care-discharge-fhir) | - | X | X | X | - | Limited to acute providers to GP Systems | " +
         "\n | [Transfer of Care Inpatient Discharge - FHIR](https://digital.nhs.uk/developer/api-catalogue/transfer-of-care-inpatient-discharge-fhir) | - | X | X | X | - | Limited to acute providers to GP Systems | " +
         "\n | [Transfer of Care Mental Health Discharge - FHIR](https://digital.nhs.uk/developer/api-catalogue/transfer-of-care-mental-health-discharge-fhir) | - | X | X | X | - | Limited to acute providers to GP Systems | " +
         "\n | [Transfer of Care Outpatient Clinic Letter - FHIR](https://digital.nhs.uk/developer/api-catalogue/transfer-of-care-outpatient-clinic-letter-fhir) | - | X | X | X | - | Limited to acute providers to GP Systems | " +
         "\n | [Digital Medicine - FHIR](https://digital.nhs.uk/developer/api-catalogue/digital-medicine-fhir) | - | X | X | X | - | Limited to pharmacy providers to GP Systems. Includes the following message types <ul><li>Immunisation</li><li>emergency medication dispensed without prescription</li><li>minor illness referral consultation</li> </ul> | " +
         "\n | [Social Care Assessment, Discharge and Withdrawal](https://data.developer.nhs.uk/specifications/sc-fhir-5.3/Chapter.1.About/index.html) | - | - | - | - | X | FHIR DSTU2. Limited to interactions to Social Care providers. Includes the following message types <ul><li>Admission Notice</li><li>Assessment Notice</li><li>Discharge Notice </li><li>Withdrawal Notice</li> </ul> | "
        val oas = OpenAPI()
            .info(
                Info()
                    .title(fhirServerProperties.server.name)
                    .version(fhirServerProperties.server.version)
                    .description(
                    ""        )
                    .termsOfService("http://swagger.io/terms/")
                    .license(License().name("Apache 2.0").url("http://springdoc.org"))
            )

        oas.addServersItem(
            Server().description(fhirServerProperties.server.name).url(fhirServerProperties.server.baseUrl)
        )
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name(PIX)
                .description("[HL7 FHIR Foundation Module](https://hl7.org/fhir/foundation-module.html) \n"
                        + " [IHE PIXm](https://profiles.ihe.net/ITI/PIXm/) \n"
                        + " [IHE PMIR](https://build.fhir.org/ig/IHE/ITI.PMIR/)")
                       )
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name(BUNDLE)
                .description("[FHIR Message](https://www.hl7.org/fhir/r4/messaging.html) and \n"
                        + " [FHIR Transaction](https://www.hl7.org/fhir/r4/http.html#transaction). If the payload is defined, then FHIR Message/\$process-message should be used. Ad-hoc transfers should use FHIR Transaction."
                )
        )


        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name(ADT)
                .description("[HL7 FHIR Foundation Module](https://hl7.org/fhir/foundation-module.html) \n"
                       )
        )
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name(FORMS)
                .description("[HL7 FHIR Structured Data Capture](http://hl7.org/fhir/uv/sdc/) \n"
                )
        )
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name(MHD)
                .description(
                    "[HL7 FHIR Foundation Module](https://hl7.org/fhir/foundation-module.html) \n"
                            + " [IHE MHD ITI-67 and ITI-68](https://profiles.ihe.net/ITI/MHD/ITI-67.html)")
        )
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name(WORKFLOW)
                .description(
                    "[HL7 FHIR Workflow](http://hl7.org/fhir/R4/workflow-module.html) \n"
                            + " [HL7 FHIR Structure Data Capture](http://hl7.org/fhir/uv/sdc/workflow.html)")
        )
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name(CARE )
                .description("[HL7 FHIR Administration Module](https://www.hl7.org/fhir/R4/administration-module.html) \n"
                        + " [IHE DCTM](https://www.ihe.net/uploadedFiles/Documents/PCC/IHE_PCC_Suppl_DCTM.pdf)")
        )

        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name(COMMUNICATION)
                .description("[HL7 FHIR Foundation Module](https://hl7.org/fhir/foundation-module.html) \n"
                        + " [IHE mACM](https://www.ihe.net/uploadedFiles/Documents/ITI/IHE_ITI_Suppl_mACM.pdf)")
        )

/*
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name("HL7 FHIR Subscription")
                .description("[HL7 FHIR Subscription](https://www.hl7.org/fhir/r4/subscription.html) \n"
                        + " [IHE PMIR ITI-94](https://build.fhir.org/ig/IHE/ITI.PMIR/ITI-94.html)")
        )
*/
        oas.path("/FHIR/R4/metadata",PathItem()
            .get(
                Operation()
                    .addTagsItem(APIM)
                    .summary("server-capabilities: Fetch the server FHIR CapabilityStatement").responses(getApiResponses())))


        var examples = LinkedHashMap<String,Example?>()
        examples.put("EPS - Prescription Order",
            Example().value(FHIRExamples().loadExample("prescription-order.json",ctx))
        )
        examples.put("EPS - Dispense Notification",
            Example().value(FHIRExamples().loadExample("dispense-notification.json",ctx))
        )
        examples.put("Provide Document Bundle Message (PDF)",
            Example().value(FHIRExamples().loadExample("Bundle-patient-document.json",ctx))
        )
        examples.put("Provide Document Bundle Message (FHIR Document STU3)",
            Example().value(FHIRExamples().loadExample("document-message-TOC.json",ctx))
        )
        examples.put("Daily Activity Report (Observations)",
            Example().value(FHIRExamples().loadExample("Bundle-message-dailyReport.json",ctx))
        )


        val processMessageItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(BUNDLE)
                    .summary("FHIR Message. Send a predefined collection of FHIR resources for processing")
                    .description( "See [process-message](https://simplifier.net/guide/nhsdigital/Home/FHIRAssets/AllAssets/OperationDefinition/process-message) \n\n"+
                            englandFHIRMessage
                    )
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examples)
                                .schema(StringSchema()))
                        .addMediaType("application/fhir+xml",
                            MediaType()
                                .schema(StringSchema()))
                    )))

        oas.path("/FHIR/R4/\$process-message",processMessageItem)

        val examplesPatient104 = LinkedHashMap<String,Example?>()

        examplesPatient104.put("Add or revise Patient",
            Example().value(FHIRExamples().loadExample("patient-9000000009.json",ctx))
        )

        val examplesPatient93 = LinkedHashMap<String,Example?>()

        examplesPatient93.put("Add Patient - NHS Number not known (Dept ED )",
            Example().value(FHIRExamples().loadExample("patient-MRN-567890.json",ctx))
        )
        examplesPatient93.put("Add/Update Patient - NHS Number traced via PDS (Dept IN)",
            Example().value(FHIRExamples().loadExample("patient-9000000025.json",ctx))
        )
        examplesPatient93.put("Merge Patient (Trust PAS)",
            Example().value(FHIRExamples().loadExample("patient-MRN-567890-Merge.json",ctx))
        )


        // Patient


        val patientItem = PathItem()
            .put(
                Operation()
                    .addTagsItem(PIX)
                    .summary("Add or Revise Patient (IHE ITI-104)")
                    .description("This message is implemented as an HTTP conditional update operation from the Patient Identity Source to the Patient Identifier Cross-reference Manager")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("identifier")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Conditional Update")
                        .schema(StringSchema())
                        .example("identifier=https://fhir.nhs.uk/Id/nhs-number|9000000009")
                    )
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesPatient104)
                                .schema(StringSchema()))
                        .addMediaType("application/fhir+xml",
                            MediaType()
                                .schema(StringSchema()))
                    )))
            .post(
                Operation()
                    .addTagsItem(PIX)
                    .summary("Add/Update/Merge Patient (IHE ITI-93)")
                    .description("Note: PMIR suggests using a urn:ihe:iti:pmir:2019:patient-feed FHIR Message. This message contains a FHIR Bundle which holds the http method POST/PUT/DEL and a Patient resource. \n"
                    + "This example API is only showing a FHIR RESTful version")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesPatient93)
                                .schema(StringSchema()))
                        .addMediaType("application/fhir+xml",
                            MediaType()
                                .schema(StringSchema()))
                    )))

        oas.path("/FHIR/R4/Patient",patientItem)

        val examplesEncounter = LinkedHashMap<String,Example?>()
        examplesEncounter.put("Hospital admission",
            Example().value(FHIRExamples().loadExample("Encounter.json",ctx))
        )

        val encounterItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(ADT)
                    .summary("Encounter Event")
                    .description("Note: PMIR suggests using a urn:ihe:iti:pmir:2019:patient-feed FHIR Message. This message contains a FHIR Bundle which holds the http method POST/PUT/DEL and a Patient resource. \n"
                            + "This example API is only showing a FHIR RESTful version")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesEncounter)
                                .schema(StringSchema()))
                        .addMediaType("application/fhir+xml",
                            MediaType()
                                .schema(StringSchema()))
                    )))

        oas.path("/FHIR/R4/Encounter",encounterItem)

        val examplesCommunication = LinkedHashMap<String,Example?>()
        examplesCommunication.put("Appointment Reminder",
            Example().value(FHIRExamples().loadExample("Communication-Appointment.json",ctx))
        )
        examplesCommunication.put("COVID-19 Alert",
            Example().value(FHIRExamples().loadExample("Communication-COVID19.json",ctx))
        )

        val examples2 = LinkedHashMap<String,Example?>()

        examples2.put("Provide Document Bundle with Comprehensive metadata of one document",
            Example().value(FHIRExamples().loadExample("MHD-transaction.json",ctx))
        )
        examples2.put("UKCore-Bundle-MichaelJonesSpecimen-Example",
            Example().value(FHIRExamples().loadExample("UKCore-Bundle-MichaelJonesSpecimen-Example.json",ctx))
        )
        examples2.put("UKCore-Bundle-MichaelJonesRequest-Example_v3_message",
            Example().value(FHIRExamples().loadExample("UKCore-Bundle-MichaelJonesRequest-Example_v3_message.json",ctx))
        )
        examples2.put("UKCore-Bundle-MichaelJonesRequest-Example_minimal",
            Example().value(FHIRExamples().loadExample("UKCore-Bundle-MichaelJonesRequest-Example_minimal.json",ctx))
        )
        examples2.put("Bundle-transaction-physicalActivity",
            Example().value(FHIRExamples().loadExample("Bundle-transaction-physicalActivity.json",ctx))
        )
        examples2.put("Bundle-Transaction-ClinicalObservations",
            Example().value(FHIRExamples().loadExample("Bundle-Transaction-ClinicalObservations.json",ctx))
        )
        val transactionItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(BUNDLE)
                    .summary("FHIR Transaction. Send a FHIR transaction Bundle to a Repository")
                    .description("API is for illustration only. \n See [transaction](https://www.hl7.org/fhir/R4/http.html#transaction)")
                    .responses(getApiResponses())
                    .requestBody(
                        RequestBody().content(Content()
                            .addMediaType("application/fhir+json",
                                MediaType()
                                    .examples(examples2)
                                    .schema(StringSchema()))
                            .addMediaType("application/fhir+xml",
                                MediaType()
                                    .schema(StringSchema()))
                        )))


        oas.path("/FHIR/R4/",transactionItem)

        // Binary


        val binExampl = LinkedHashMap<String,Example?>()
        binExampl.put("Hello World",Example().value("Hello World"))
        val binaryItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(MHD)
                    .summary("This is a raw http POST. POST of a FHIR Binary can be used, with application/fhir+json Content-Type header")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("Content-Type")
                        .`in`("header")
                        .required(true)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Mime type of the document/image")
                        .schema(StringSchema())
                    )
                    .requestBody(
                        RequestBody().content(Content()
                            .addMediaType("text/plain",
                                MediaType()
                                    .examples(binExampl)
                                    .schema(StringSchema()))
                        ))
            )

        oas.path("/FHIR/R4/Binary",binaryItem)

        // DocumentReference

        val examplesDSUB = LinkedHashMap<String,Example?>()

        examplesDSUB["Document Notification (PDF)"] =
            Example().value(FHIRExamples().loadExample("documentReference-DSUB.json",ctx))
        examplesDSUB["Document Notification (FHIR Document STU3)"] =
            Example().value(FHIRExamples().loadExample("documentReference-TOC-Notification.json",ctx))

        val documentReferenceItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(MHD)
                    .summary("Document Notification")
                    .description("See [Events and Notifications](http://lb-hl7-tie-1794188809.eu-west-2.elb.amazonaws.com/) for FHIR Subscription interactions")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesDSUB)
                                .schema(StringSchema()))
                        .addMediaType("application/fhir+xml",
                            MediaType()
                                .schema(StringSchema()))
                    )))
        oas.path("/FHIR/R4/DocumentReference",documentReferenceItem)


        //Care Plan

        var carePlanItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(CARE)
                    .summary("")
                    .description("This transaction is used to find a CarePlan resource.")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("patient")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Who episode/stay is for")
                        .schema(StringSchema())
                        .example("073eef49-81ee-4c2e-893b-bc2e4efd2630")
                    )
                    .addParametersItem(Parameter()
                        .name("patient:identifier")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Who episode/stay is for. `https://fhir.nhs.uk/Id/nhs-number|{nhsNumber}` ")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("date")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Time period episode covers")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("status")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("planned | waitlist | active | onhold | finished | cancelled | entered-in-error")
                        .schema(StringSchema())
                    )

            )

        examples = LinkedHashMap<String,Example?>()

        examples.put("Create Diabetes CarePlan",
            Example().value(FHIRExamples().loadExample("CarePlan-Diabetes.json",ctx))
        )
        carePlanItem
            .post(
                Operation()
                    .addTagsItem(CARE)
                    .summary("")
                    .description("This transaction is used to update or to create a EpisodeOfCare resource.")
                    .responses(getApiResponses())
                    .requestBody(
                        RequestBody().content(Content()
                            .addMediaType("application/fhir+json",
                                MediaType()
                                    .examples(examples)
                                    .schema(StringSchema()))
                        )))

        oas.path("/FHIR/R4/CarePlan",carePlanItem)
        carePlanItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(CARE)
                    .description("This transaction is used to retrieve a specific CarePlan resource using a known FHIR CarePlan " +
                            "resource id.")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("id")
                        .`in`("path")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The ID of the resource")
                        .schema(StringSchema())
                        .example("b664a27a-2117-4b13-a0d1-cc0b98e4532b")
                    )
            )

        carePlanItem.put(
            Operation()
                .addTagsItem(CARE)
                .description("This transaction is used to update or to create a CareTeam resource. A CareTeam resource is " +
                        "submitted to a Care Team Service where the update or creation is handled.")
                .responses(getApiResponses())
                .addParametersItem(Parameter()
                    .name("id")
                    .`in`("path")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The ID of the resource")
                    .schema(StringSchema())
                    .example("c4a7c5cb-ea81-4e52-8171-22f11fa5caf0")
                )
                .requestBody(
                    RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .schema(StringSchema()))
                    )))
        carePlanItem.delete(Operation()
            .addTagsItem(CARE)
            .summary("Delete CareTeam")
            .responses(getApiResponses())
            .addParametersItem(Parameter()
                .name("id")
                .`in`("path")
                .required(false)
                .style(Parameter.StyleEnum.SIMPLE)
                .description("The id of the CareTeam to be deleted")
                .schema(StringSchema())))

        oas.path("/FHIR/R4/CarePlan/{id}",carePlanItem)

        // Case Load Episode of Care

        val episodeOfCareItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(CARE)
                    .summary("")
                    .description("This transaction is used to find a Episode resource.")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("patient")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Who episode/stay is for")
                        .schema(StringSchema())
                        .example("073eef49-81ee-4c2e-893b-bc2e4efd2630")
                    )
                    .addParametersItem(Parameter()
                        .name("patient:identifier")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Who episode/stay is for. `https://fhir.nhs.uk/Id/nhs-number|{nhsNumber}` ")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("date")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Time period episode covers")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("status")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("planned | waitlist | active | onhold | finished | cancelled | entered-in-error")
                        .schema(StringSchema())
                    )

            )

        examples = LinkedHashMap()

        examples["Create Diabetes (virtual ward) Episode"] =
            Example().value(FHIRExamples().loadExample("EpisodeOfCare-AcuteHospital-Diabetes.json",ctx))
        episodeOfCareItem
            .post(
                Operation()
                    .addTagsItem(CARE)
                    .summary("")
                    .description("This transaction is used to update or to create a EpisodeOfCare resource.")
                    .responses(getApiResponses())
                    .requestBody(
                        RequestBody().content(Content()
                            .addMediaType("application/fhir+json",
                                MediaType()
                                    .examples(examples)
                                    .schema(StringSchema()))
                        )))

        oas.path("/FHIR/R4/EpisodeOfCare",episodeOfCareItem)



/// Care Teams


        var careTeamItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(CARE)
                    .summary("[PCC-47]")
                    .description("This transaction is used to retrieve a specific CareTeam resource using a known FHIR CareTeam " +
                            "resource id.")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("id")
                        .`in`("path")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The ID of the resource")
                        .schema(StringSchema())
                        .example("0bd736ed-d9d5-44f3-8b93-73c1519191b1")
                    )
            )
        val examplesPUT = LinkedHashMap<String,Example?>()
        examplesPUT["Update a Patient Care Team (Acute Trust)"] =
            Example().value(FHIRExamples().loadExample("careTeam-put.json",ctx))
        careTeamItem.put(
            Operation()
                .addTagsItem(CARE)
                .summary("[PCC-45]")
                .description("This transaction is used to update or to create a CareTeam resource. A CareTeam resource is " +
                        "submitted to a Care Team Service where the update or creation is handled.")
                .responses(getApiResponses())
                .addParametersItem(Parameter()
                    .name("id")
                    .`in`("path")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The ID of the resource")
                    .schema(StringSchema())
                    .example("0bd736ed-d9d5-44f3-8b93-73c1519191b1")
                )
                .requestBody(
                    RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesPUT)
                                .schema(StringSchema()))
                    )))
        careTeamItem.delete(Operation()
            .addTagsItem(CARE)
            .summary("Delete CareTeam")
            .responses(getApiResponses())
            .addParametersItem(Parameter()
                .name("id")
                .`in`("path")
                .required(false)
                .style(Parameter.StyleEnum.SIMPLE)
                .description("The id of the CareTeam to be deleted")
                .schema(StringSchema())))

        oas.path("/FHIR/R4/CareTeam/{id}",careTeamItem)

        careTeamItem = PathItem()
            .get(
                Operation()
                    .addTagsItem(CARE)
                    .summary("[PCC-46]")
                    .description("This transaction is used to find a CareTeam resource. The Care Team Contributor searches for a " +
                            "CareTeam resource of interest. A CareTeam resource located by search may then be retrieved for " +
                            "viewing or updating.")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("patient")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Who care team is for")
                        .schema(StringSchema())
                        .example("073eef49-81ee-4c2e-893b-bc2e4efd2630")
                    )
                    .addParametersItem(Parameter()
                        .name("patient:identifier")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Who care team is for. `https://fhir.nhs.uk/Id/nhs-number|{nhsNumber}` ")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("date")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Time period team covers")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("status")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("proposed | active | suspended | inactive | entered-in-error")
                        .schema(StringSchema())
                    )

            )

        examples = LinkedHashMap()
        examples["Create a Patient Care Team (Acute Trust)"] =
            Example().value(FHIRExamples().loadExample("careTeam-post.json",ctx))
        careTeamItem
            .post(
                Operation()
                    .addTagsItem(CARE)
                    .summary("[PCC-45]")
                    .description("This transaction is used to update or to create a CareTeam resource. A CareTeam resource is " +
                            "submitted to a Care Team Service where the update or creation is handled.")
                    .responses(getApiResponses())
                    .requestBody(
                        RequestBody().content(Content()
                            .addMediaType("application/fhir+json",
                                MediaType()
                                    .examples(examples)
                                    .schema(StringSchema()))
                        )))

        oas.path("/FHIR/R4/CareTeam",careTeamItem)


        val communicationRequestItem = PathItem()

        communicationRequestItem.put(
            Operation()
                .addTagsItem(COMMUNICATION)
                .responses(getApiResponses())
                .addParametersItem(Parameter()
                    .name("id")
                    .`in`("path")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The ID of the resource")
                    .schema(StringSchema())

                )
                .requestBody(
                    RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .schema(StringSchema()))
                    )))
        communicationRequestItem.delete(Operation()
            .addTagsItem(COMMUNICATION)
            .summary("Delete Communication")
            .responses(getApiResponses())
            .addParametersItem(Parameter()
                .name("id")
                .`in`("path")
                .required(false)
                .style(Parameter.StyleEnum.SIMPLE)
                .description("The id of the Communication to be deleted")
                .schema(StringSchema())))

        communicationRequestItem.get(Operation()
            .addTagsItem(COMMUNICATION)
            .summary("Read Communication")
            .responses(getApiResponses())
            .addParametersItem(Parameter()
                .name("id")
                .`in`("path")
                .required(false)
                .style(Parameter.StyleEnum.SIMPLE)
                .description("The id of the Communication")
                .schema(StringSchema())))

        oas.path("/FHIR/R4/Communication/{id}",communicationRequestItem)

        val communicationItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(COMMUNICATION)
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesCommunication)
                                .schema(StringSchema()))
                    )))
            .get(
                Operation()
                    .addTagsItem(COMMUNICATION)
                    .addParametersItem(Parameter()
                        .name("patient")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Message subject")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("patient:identifier")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Message subject `https://fhir.nhs.uk/Id/nhs-number|{nhsNumber}` ")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("recipient")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Message recipient")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("sender")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Message sender")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("status")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Message status")
                        .schema(StringSchema())
                    )
                    .responses(getApiResponses())
            )

        oas.path("/FHIR/R4/Communication",communicationItem)

        val examplesQuestionnaireResponse = LinkedHashMap<String,Example?>()

        examplesQuestionnaireResponse["Simple Blood Pressure"] =
            Example().value(FHIRExamples().loadExample("QuestionnaireResponse-patient-simple-blood-pressure.json",ctx))
        val questionnaireResponseItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(FORMS)
                    .summary("Submit Completed Form (IHE ITI-35)")
                    .description("The results of a completed form")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesQuestionnaireResponse )
                                .schema(StringSchema()))
                        .addMediaType("application/fhir+xml",
                            MediaType()
                                .schema(StringSchema()))
                    )))


        oas.path("/FHIR/R4/QuestionnaireResponse",questionnaireResponseItem)
        val examplesQuestionnaireResponseExtract = LinkedHashMap<String,Example?>()

        examplesQuestionnaireResponseExtract["Vital Signs"] =
            Example().value(FHIRExamples().loadExample("QuestionnaireResponse-vital-signs.json",ctx))
        val questionnaireResponseExtractItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(FORMS)
                    .summary("Form Data Extraction")
                    .description("[Form Data Extraction](http://hl7.org/fhir/uv/sdc/extraction.html) Allows data captured in a QuestionnaireResponse to be extracted and used to create or update other FHIR resources - allowing the data to be more easily searched, compared and used by other FHIR systems")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesQuestionnaireResponseExtract )
                                .schema(StringSchema()))
                        .addMediaType("application/fhir+xml",
                            MediaType()
                                .schema(StringSchema()))
                    )))

        oas.path("/FHIR/R4/QuestionnaireResponse/\$extract",questionnaireResponseExtractItem)


        // Questionnaire

        val examplesQuestionnaire = LinkedHashMap<String,Example?>()

        examplesQuestionnaire["Vital Signs SDC"] =
            Example().value(FHIRExamples().loadExample("Vital-Signs-Findings-SDC.json",ctx))

            oas.path("/FHIR/R4/Questionnaire",
            PathItem().
            get(Operation()
                .addTagsItem(FORMS)
                .summary("Finding a Questionnaire")
                .description("[Finding a Questionnaire](http://hl7.org/fhir/uv/sdc/search.html) Before a questionnaire can be filled out, it must first be 'found'. In some cases, workflow will dictate the specific Questionnaire to use - it will be pointed to by a Task to be performed, be included in a CarePlan, referenced by a PlanDefinition or made available in some other way. However, often users will need to search a registry or other repository to find the desired form, clinical instrument, etc. This portion of the SDC specification sets expectations for systems that support storing questionnaires and allowing client systems to search against their repository of questionnaires to find those that meet specified criteria. ")
                .responses(getApiResponses())
                .addParametersItem(Parameter()
                    .name("url")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The uri that identifies the Questionnaire ")
                    .schema(StringSchema().format("token"))
                    .example("https://example.fhir.nhs.uk/Questionnaire/86923-0"))
                .addParametersItem(Parameter()
                    .name("code")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("A code that corresponds to one of its items in the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("context")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("A use context assigned to the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("date")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The questionnaire publication date")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("identifier")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("External identifier for the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("publisher")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("Name of the publisher of the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("status")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The current status of the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("title")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The human-friendly name of the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("version")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The business version of the questionnaire")
                    .schema(StringSchema()))
                .addParametersItem(Parameter()
                    .name("definition")
                    .`in`("query")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("ElementDefinition - details for the item")
                    .schema(StringSchema()))
            )
                .post(Operation()
                    .addTagsItem(FORMS)
                    .summary("Add Questionnaire")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesQuestionnaire )
                                .schema(StringSchema()))
                    )))
        )

        oas.path("/FHIR/R4/Questionnaire/{id}",
            PathItem().put(Operation()
                .addTagsItem(FORMS)
                .summary("Update Questionnaire")
                .responses(getApiResponses())
                .addParametersItem(Parameter()
                    .name("id")
                    .`in`("path")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The id of the Questionnaire to be updated")
                    .example("0d9fccea-9c98-4e61-b3e0-bc9b3a9db675")
                    .schema(StringSchema()))
                .requestBody(RequestBody().content(Content()
                    .addMediaType("application/fhir+json",
                        MediaType()
                            .examples(examplesQuestionnaire )
                            .schema(StringSchema()))
                )))
                .get(Operation()
                    .addTagsItem(FORMS)
                    .summary("Read Questionnaire")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("id")
                        .`in`("path")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The id of the Questionnaire to be retrieved")
                        .schema(StringSchema().example("56969434-1980-4262-b6a7-ed1c8aca5ec2"))))
                .delete(Operation()
                    .addTagsItem(FORMS)
                    .summary("Delete Questionnaire")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("id")
                        .`in`("path")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The id of the Questionnaire to be deleted")
                        .schema(StringSchema()))))



        // ServiceRequest

        val examplesPOSTServiceRequest = LinkedHashMap<String,Example?>()
        examplesPOSTServiceRequest["Create ServiceRequest"] =
            Example().value(FHIRExamples().loadExample("ServiceRequest-virtualWards.json",ctx))

        val examplesPUTServiceRequest= LinkedHashMap<String,Example?>()
        examplesPUTServiceRequest["Update ServiceRequest"] = Example().value(FHIRExamples().loadExample("ServiceRequest-virtualWards.json",ctx))

        var serviceRequestItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(WORKFLOW)
                    .summary("Create Service Request")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesPOSTServiceRequest )
                                .schema(StringSchema()))
                    )))

        oas.path("/FHIR/R4/ServiceRequest",serviceRequestItem)

        serviceRequestItem = PathItem()
        serviceRequestItem.put(
            Operation()
                .addTagsItem(WORKFLOW)
                .summary("Update ServiceRequest")
                .description("This transaction is used to update a ServiceRequest")
                .responses(getApiResponses())
                .addParametersItem(Parameter()
                    .name("id")
                    .`in`("path")
                    .required(false)
                    .style(Parameter.StyleEnum.SIMPLE)
                    .description("The ID of the resource")
                    .schema(StringSchema())
                    .example("0bd736ed-d9d5-44f3-8b93-73c1519191b1")
                )
                .requestBody(
                    RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesPUTServiceRequest)
                                .schema(StringSchema()))
                    )))

        oas.path("/FHIR/R4/ServiceRequest/{id}",serviceRequestItem)


        val examplesPOSTTask = LinkedHashMap<String,Example?>()
        examplesPOSTTask["Form Complete Task"] =
            Example().value(FHIRExamples().loadExample("Task-formComplete.json",ctx))

        val examplesPUTTask = LinkedHashMap<String,Example?>()
        examplesPUTTask["Form Complete Task Updated to be completed"] = Example().value(FHIRExamples().loadExample("Task-formComplete-completed.json",ctx))

        val taskItem = PathItem()
            .put(
                Operation()
                    .addTagsItem(WORKFLOW)
                    .summary("Complete Form Request (Task) - Completed")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("identifier")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Conditional Update")
                        .schema(StringSchema())
                        .example("identifier=https://tools.ietf.org/html/rfc4122|06570d9e-1dd7-49b6-8276-c903eef74b73")
                    )
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesPUTTask )
                                .schema(StringSchema()))
                    )))
            .post(
                Operation()
                    .addTagsItem(WORKFLOW)
                    .summary("Complete Form Request (Task)")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesPOSTTask )
                                .schema(StringSchema()))
                    )))

        oas.path("/FHIR/R4/Task",taskItem)

        /*

        Feb 2023 Disabled Subscription for hack


        val examplesSubscriptionCreate = LinkedHashMap<String,Example?>()
        examplesSubscriptionCreate.put("Add PMIR Patient Subscription",
            Example().value(FHIRExamples().loadExample("subscription-pmir-create.json",ctx))
        )
        val examplesSubscriptionUpdate = LinkedHashMap<String,Example?>()
        examplesSubscriptionUpdate.put("Disable PMIR Patient Subscription",
            Example().value(FHIRExamples().loadExample("subscription-pmir-disable.json",ctx))
        )
        var subscriptionItem = PathItem()
            .post(
                Operation()
                    .addTagsItem("HL7 FHIR Subscription")
                    .summary("Create Subscription")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesSubscriptionCreate)
                                .schema(StringSchema()))
                    )))

        oas.path("/FHIR/R4/Subscription",subscriptionItem)

        subscriptionItem = PathItem()
            .get(
                Operation()
                    .addTagsItem("HL7 FHIR Subscription")
                    .summary("Read Subscription")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("id")
                        .`in`("path")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("id of the resource")
                        .schema(StringSchema())
                        .example("63c2c7f9-9432-4028-95fc-5981d0ef3026")
                    ))
            .delete(
                Operation()
                    .addTagsItem("HL7 FHIR Subscription")
                    .summary("Delete Subscription")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("id")
                        .`in`("path")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("id of the resource")
                        .schema(StringSchema())
                        .example("63c2c7f9-9432-4028-95fc-5981d0ef3026")
                    ))
            .put(
                Operation()
                    .addTagsItem("HL7 FHIR Subscription")
                    .summary("Update Subscription")
                    .responses(getApiResponses())
                    .addParametersItem(Parameter()
                        .name("id")
                        .`in`("path")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("id of the resource")
                        .schema(StringSchema())
                        .example("63c2c7f9-9432-4028-95fc-5981d0ef3026")
                    )
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesSubscriptionUpdate)
                                .schema(StringSchema()))
                    )))


        oas.path("/FHIR/R4/Subscription/{id}",subscriptionItem)
 */
        return oas
    }

    fun getApiResponses(): ApiResponses {

        val response200 = ApiResponse()
        response200.description = "OK"
        val exampleList = mutableListOf<Example>()
        exampleList.add(Example().value("{}"))
        response200.content =
            Content().addMediaType("application/fhir+json", MediaType().schema(StringSchema()._default("{}")))
                .addMediaType("application/fhir+xml", MediaType().schema(StringSchema()._default("")))
        return ApiResponses().addApiResponse("200", response200)
    }
}
