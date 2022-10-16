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

    var FHIRSERVER = "HL7 FHIR Orchestration Engine"
    @Bean
    open fun customOpenAPI(
        fhirServerProperties: FHIRServerProperties,
       // restfulServer: FHIRR4RestfulServer
    ): OpenAPI? {

        val oas = OpenAPI()
            .info(
                Info()
                    .title("NHS Digital Interoperability Standards -"+fhirServerProperties.server.name)
                    .version(fhirServerProperties.server.version)
                    .description(
                                "\n [UK Core Implementation Guide (0.5.1)](https://simplifier.net/guide/ukcoreimplementationguide0.5.0-stu1/home?version=current)"
                                + "\n\n [NHS Digital Implementation Guide (2.6.0)](https://simplifier.net/guide/nhsdigital?version=2.6.0)"
                    )
                    .termsOfService("http://swagger.io/terms/")
                    .license(License().name("Apache 2.0").url("http://springdoc.org"))
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
                    .description("[process-message](https://simplifier.net/guide/nhsdigital/Home/FHIRAssets/AllAssets/OperationDefinition/process-message)")
                    .responses(getApiResponses())
                    .requestBody(RequestBody().content(Content()
                        .addMediaType("application/fhir+json",
                            MediaType()
                                .examples(examples)
                                .schema(StringSchema()))
                    )))

        oas.path("/FHIR/R4/\$process-message",processMessageItem)

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
