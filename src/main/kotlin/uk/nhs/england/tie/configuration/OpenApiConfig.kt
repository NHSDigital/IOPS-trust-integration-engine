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
open class OpenApiConfig(@Qualifier("R4") val ctx : FhirContext) {

    var PIX = "Patient Demographics Events"
    var ADT = "Admission and Discharge Events (ADT)"
    var BUNDLE = "Batch Record Transfer"
    var MHD = "Documents"
    var FORMS = "Structured Data Capture"
    var APIM = "Security and API Management"
    @Bean
    open fun customOpenAPI(
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
        /*
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name("UKCore Alert Communication Management")
                .description("[HL7 FHIR Foundation Module](https://hl7.org/fhir/foundation-module.html) \n"
                        + " [IHE mACM](https://www.ihe.net/uploadedFiles/Documents/ITI/IHE_ITI_Suppl_mACM.pdf)")
        )
*/
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


        val examples = LinkedHashMap<String,Example?>()
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


        var patientItem = PathItem()
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
                    )))

        oas.path("/FHIR/R4/Encounter",encounterItem)

        val examplesCommunicationRequest = LinkedHashMap<String,Example?>()
        examplesCommunicationRequest.put("ITI-84 Appointment Reminder",
            Example().value(FHIRExamples().loadExample("CommunicationRequest.json",ctx))
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
                        )))


        oas.path("/FHIR/R4/",transactionItem)

        // Binary


        val binExampl = LinkedHashMap<String,Example?>()
        binExampl.put("Hello World",Example().value("Hello World"))
        var binaryItem = PathItem()
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

        examplesDSUB.put("Document Notification (PDF)",
            Example().value(FHIRExamples().loadExample("documentReference-DSUB.json",ctx))
        )
        examplesDSUB.put("Document Notification (FHIR Document STU3)",
            Example().value(FHIRExamples().loadExample("documentReference-TOC-Notification.json",ctx))
        )

        var documentReferenceItem = PathItem()
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
                    )))
        oas.path("/FHIR/R4/DocumentReference",documentReferenceItem)



        /*
        val communicationRequestItem = PathItem()
            .post(
                Operation()
                    .addTagsItem("UKCore Alert Communication Management")
                    .summary("Mobile Report Alert (IHE ITI-84)")
                    .description("This doesn't send the actual text message, that is down to system format (e.g. SMS, email, etc)")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesCommunicationRequest)
                                .schema(StringSchema()))
                    )))

        oas.path("/FHIR/R4/CommunicationRequest",communicationRequestItem)

        val communicationItem = PathItem()
            .get(
                Operation()
                    .addTagsItem("UKCore Alert Communication Management")
                    .summary("Query Report Alert (IHE ITI-85)")
                    .description("This allows querying results of a CommunicationRequest")
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
*/
        val examplesQuestionnaireResponse = LinkedHashMap<String,Example?>()

        examplesQuestionnaireResponse .put("Simple Blood Pressure",
            Example().value(FHIRExamples().loadExample("QuestionnaireResponse-patient-simple-blood-pressure.json",ctx))
        )
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
                    )))


        oas.path("/FHIR/R4/QuestionnaireResponse",questionnaireResponseItem)
/*

        val examplesTask = LinkedHashMap<String,Example?>()
        examplesTask.put("Form Complete Task",
            Example().value(FHIRExamples().loadExample("Task-formComplete.json",ctx))
        )

        val taskItem = PathItem()
            .put(
                Operation()
                    .addTagsItem("UKCore Alert Communication Management")
                    .summary("Complete Form Request (Task)")
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
                                .examples(examplesTask )
                                .schema(StringSchema()))
                    )))
            .get(
                Operation()
                    .addTagsItem("UKCore Alert Communication Management")
                    .summary("Query Tasks")
                    .addParametersItem(Parameter()
                        .name("patient")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Search by patient")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("owner")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Search by task owner")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("requester")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Search by task requester")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("status")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("Search by task status")
                        .schema(StringSchema())
                    )
                    .responses(getApiResponses())
            )

        oas.path("/FHIR/R4/Task",taskItem)
*/

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

    fun getApiResponses() : ApiResponses {

        val response200 = ApiResponse()
        response200.description = "OK"
        val exampleList = mutableListOf<Example>()
        exampleList.add(Example().value("{}"))
        response200.content = Content().addMediaType("application/fhir+json", MediaType().schema(StringSchema()._default("{}")))
        val apiResponses = ApiResponses().addApiResponse("200",response200)
        return apiResponses
    }
}
