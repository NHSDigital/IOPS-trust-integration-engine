package uk.nhs.nhsdigital.pmir.configuration

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
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.nhs.nhsdigital.pmir.util.FHIRExamples


@Configuration
open class OpenApiConfig(@Qualifier("R4") val ctx : FhirContext) {

    var FHIRSERVER = "HL7 FHIR Message Notifications"
    @Bean
    open fun customOpenAPI(
        fhirServerProperties: FHIRServerProperties,
       // restfulServer: FHIRR4RestfulServer
    ): OpenAPI? {

        val oas = OpenAPI()
            .info(
                Info()
                    .title(fhirServerProperties.server.name)
                    .version(fhirServerProperties.server.version)
                    .description(
                        "\n\n The results of events or notifications posted from this OAS can be viewed on [Query for Existing Patient Data](http://lb-fhir-facade-926707562.eu-west-2.elb.amazonaws.com/)"
                        + "\n\n To view example patients (with example NHS Numbers), see **Patient Demographics Query** section of [Query for Existing Patient Data](http://lb-fhir-facade-926707562.eu-west-2.elb.amazonaws.com/)"

                                + "\n\n For ODS, GMP and GMP codes, see [Care Services Directory](http://lb-fhir-mcsd-1736981144.eu-west-2.elb.amazonaws.com/). This OAS also includes **Care Teams Management**"
                                + "\n\n For Document Notifications, see [Access to Health Documents](http://lb-fhir-mhd-1617422145.eu-west-2.elb.amazonaws.com/)."
                                + "\n\n ## FHIR Implementation Guides"
                                + "\n\n [UK Core Implementation Guide (0.5.1)](https://simplifier.net/guide/ukcoreimplementationguide0.5.0-stu1/home?version=current)"
                                + "\n\n [NHS Digital Implementation Guide (2.6.0)](https://simplifier.net/guide/nhsdigital?version=2.6.0)"


                    )
                    .termsOfService("http://swagger.io/terms/")
                    .license(License().name("Apache 2.0").url("http://springdoc.org"))
            )


        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name("HL7 FHIR Events - Patient Identifier Cross-referencing")
                .description("[HL7 FHIR Foundation Module](https://hl7.org/fhir/foundation-module.html) \n"
                        + " [IHE PIXm ITI-104](https://profiles.ihe.net/ITI/PIXm/ITI-104.html)")
        )
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name("HL7 FHIR Events - ADT")
                .description("[HL7 FHIR Foundation Module](https://hl7.org/fhir/foundation-module.html) \n"
                       )
        )
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name("UKCore Alert Communication Management")
                .description("[HL7 FHIR Foundation Module](https://hl7.org/fhir/foundation-module.html) \n"
                        + " [IHE mACM ITI-84](https://www.ihe.net/uploadedFiles/Documents/ITI/IHE_ITI_Suppl_mACM.pdf)")
        )
        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name("HL7 FHIR Events - Patient Identity Feed")
                .description("[HL7 FHIR Foundation Module](https://hl7.org/fhir/foundation-module.html) \n"
                        + " [IHE PMIR ITI-93](https://build.fhir.org/ig/IHE/ITI.PMIR/ITI-93.html)" +
                        "")
        )

        oas.addTagsItem(
            io.swagger.v3.oas.models.tags.Tag()
                .name("HL7 FHIR Subscription")
                .description("[HL7 FHIR Subscription](https://www.hl7.org/fhir/r4/subscription.html) \n"
                        + " [IHE PMIR ITI-94](https://build.fhir.org/ig/IHE/ITI.PMIR/ITI-94.html)")
        )

        oas.path("/FHIR/R4/metadata",PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("server-capabilities: Fetch the server FHIR CapabilityStatement").responses(getApiResponses())))


        val examples = LinkedHashMap<String,Example?>()
        examples.put("PDS - Birth Notification",
            Example().value(FHIRExamples().loadExample("pds-birth-notification.json",ctx))
        )
        examples.put("PDS - Change of Address",
            Example().value(FHIRExamples().loadExample("pds-change-of-address.json",ctx))
        )
        examples.put("PDS - Death Notification",
            Example().value(FHIRExamples().loadExample("pds-death-notification.json",ctx))
        )

        examples.put("BARS - Making a referral request",
                Example().value(FHIRExamples().loadExample("Making a referral request.json",ctx))
            )
        examples.put("EPS - Prescription Order",
            Example().value(FHIRExamples().loadExample("prescription-order.json",ctx))
        )
        examples.put("EPS - Dispense Notification",
            Example().value(FHIRExamples().loadExample("dispense-notification.json",ctx))
        )
        examples.put("Pathology - Unsolicited Observations",
            Example().value(FHIRExamples().loadExample("pathology-report.json",ctx))
        )

        val processMessageItem = PathItem()
            .post(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("Send a predefined collection of FHIR resources for processing")
                    .description( "See [process-message](https://simplifier.net/guide/nhsdigital/Home/FHIRAssets/AllAssets/OperationDefinition/process-message) \n\n"+
                            " | Supported Messages | \n" +
                            " |-------| \n" +
                            " | [prescription-order](https://simplifier.net/guide/nhsdigital/Home/FHIRAssets/AllAssets/MessageDefinitions/prescription-order) |" +
                            " | [dispense-notification](https://simplifier.net/guide/nhsdigital/Home/FHIRAssets/AllAssets/MessageDefinitions/dispense-notification) |" +
                            " | [servicerequest-request](https://simplifier.net/guide/nhsbookingandreferralstandard/Home/FHIRAssets/AllAssets/AllProfiles/BARS-MessageDefinition-ServiceRequest-RequestReferral.page.md?version=current) |"
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


        val patientItem = PathItem()
            .put(
                Operation()
                    .addTagsItem("HL7 FHIR Events - Patient Identifier Cross-referencing")
                    .summary("Add or Revise Patient")
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
                    .addTagsItem("HL7 FHIR Events - Patient Identity Feed")
                    .summary("Add/Update/Merge Patient")
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
                    .addTagsItem("HL7 FHIR Events - ADT")
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

        val communicationRequestItem = PathItem()
            .post(
                Operation()
                    .addTagsItem("UKCore Alert Communication Management")
                    .summary("Mobile Report Alert")
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
                    .summary("Query Report Alert")
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

        val examplesQuestionnaireResponse = LinkedHashMap<String,Example?>()
        examplesQuestionnaireResponse .put("Patient Registration",
            Example().value(FHIRExamples().loadExample("QuestionnaireResponse-patient-registration-completed.json",ctx))
        )
        examplesQuestionnaireResponse .put("Simple Blood Pressure",
            Example().value(FHIRExamples().loadExample("QuestionnaireResponse-patient-simple-blood-pressure.json",ctx))
        )
        val questionnaireResponseItem = PathItem()
            .post(
                Operation()
                    .addTagsItem("UKCore Alert Communication Management")
                    .summary("Send Completed Form")
                    .description("The results of a completed form")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesQuestionnaireResponse )
                                .schema(StringSchema()))
                    )))
            .get(
                Operation()
                    .addTagsItem("UKCore Alert Communication Management")
                    .summary("Query Form Results")
                    .description("This allows querying results of a QuestionnaireResponse")
                    .addParametersItem(Parameter()
                        .name("patient")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The patient that is the subject of the questionnaire response")
                        .schema(StringSchema())
                    )
                    .addParametersItem(Parameter()
                        .name("questionnaire")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The questionnaire the answers are provided for")
                        .schema(StringSchema())
                        .example("https://example.fhir.nhs.uk/Questionnaire/Simple-Blood-Pressure")
                    )
                    .responses(getApiResponses())
            )

        oas.path("/FHIR/R4/QuestionnaireResponse",questionnaireResponseItem)

        val examplesQuestionnaire = LinkedHashMap<String,Example?>()
        examplesQuestionnaire .put("Patient Blood Pressure Form Definition",
            Example().value(FHIRExamples().loadExample("Questionnaire-Simple-Blood-Pressure.json",ctx))
        )
        examplesQuestionnaire .put("Patient Registration Form Definition",
            Example().value(FHIRExamples().loadExample("Questionnaire-Patient-Registration.json",ctx))
        )

        val questionnaireItem = PathItem()
            .post(
                Operation()
                    .addTagsItem("UKCore Alert Communication Management")
                    .summary("Create Form Definition")

                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examplesQuestionnaire )
                                .schema(StringSchema()))
                    )))
            .get(
                Operation()
                    .addTagsItem("UKCore Alert Communication Management")
                    .summary("Query Form Definitions")

                    .addParametersItem(Parameter()
                        .name("url")
                        .`in`("query")
                        .required(false)
                        .style(Parameter.StyleEnum.SIMPLE)
                        .description("The uri that identifies the questionnaire")
                        .schema(StringSchema())
                        .example("https://example.fhir.nhs.uk/Questionnaire/Simple-Blood-Pressure")
                    )
                    .responses(getApiResponses())
            )

        oas.path("/FHIR/R4/Questionnaire",questionnaireItem)

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
