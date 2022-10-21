package uk.nhs.nhsdigital.integrationengine.configuration

import ca.uhn.fhir.context.FhirContext
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.BooleanSchema
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
import uk.nhs.nhsdigital.integrationengine.util.FHIRExamples


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
                                "\n [UK Core Implementation Guide (0.5.1)](https://simplifier.net/guide/ukcoreimplementationguide0.5.0-stu1/home?version=current)"
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

        oas.path("/FHIR/R4/metadata",PathItem()
            .get(
                Operation()
                    .addTagsItem(FHIRSERVER)
                    .summary("server-capabilities: Fetch the server FHIR CapabilityStatement").responses(getApiResponses())))


        val examples = LinkedHashMap<String,Example?>()

        examples.put("BARS - Making a referral request",
                Example().value(FHIRExamples().loadExample("Making a referral request.json",ctx))
            )
        examples.put("EPS - Prescription Order",
            Example().value(FHIRExamples().loadExample("prescription-order.json",ctx))
        )
        examples.put("EPS - Dispense Notification",
            Example().value(FHIRExamples().loadExample("dispense-notification.json",ctx))
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

        val examplesPatient = LinkedHashMap<String,Example?>()

        examplesPatient.put("Add or revise Patient",
            Example().value(FHIRExamples().loadExample("patient-pds.json",ctx))
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
                                .examples(examplesPatient)
                                .schema(StringSchema()))
                    )))

        oas.path("/FHIR/R4/Patient",patientItem)

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
