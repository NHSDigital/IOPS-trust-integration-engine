package uk.nhs.nhsdigital.pmir

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.cors.CorsConfiguration
import uk.nhs.nhsdigital.pmir.configuration.FHIRServerProperties
import uk.nhs.nhsdigital.pmir.provider.*
import uk.nhs.nhsdigital.pmir.interceptor.AWSAuditEventLoggingInterceptor
import java.util.*
import javax.servlet.annotation.WebServlet

@WebServlet("/FHIR/R4/*", loadOnStartup = 1)
class FHIRR4RestfulServer(
    @Qualifier("R4") fhirContext: FhirContext,
    public val fhirServerProperties: FHIRServerProperties,
    public val processMessageProvider: ProcessMessageProvider,
    val patientProvider: PatientProvider,
    val subscriptionProvider: SubscriptionProvider,
    val encounterProvider: EncounterProvider,
    val communicationRequestProvider: CommunicationRequestProvider,
    val communicationProvider: CommunicationProvider,
    val questionnaireResponseProvider: QuestionnaireResponseProvider,
    val questionnaireProvider: QuestionnaireProvider,
    val taskProvider: TaskProvider
) : RestfulServer(fhirContext) {

    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        registerProvider(processMessageProvider)
        registerProvider(patientProvider)
        registerProvider(subscriptionProvider)
        registerProvider(encounterProvider)
        registerProvider(communicationRequestProvider)
        registerProvider(communicationProvider)
        registerProvider(questionnaireResponseProvider)
        registerProvider(questionnaireProvider)
        registerProvider(taskProvider)

        val awsAuditEventLoggingInterceptor =
            AWSAuditEventLoggingInterceptor(
                this.fhirContext,
                fhirServerProperties
            )
        interceptorService.registerInterceptor(awsAuditEventLoggingInterceptor)

        val config = CorsConfiguration()
        config.addAllowedHeader("x-fhir-starter")
        config.addAllowedHeader("Origin")
        config.addAllowedHeader("Accept")
        config.addAllowedHeader("X-Requested-With")
        config.addAllowedHeader("Content-Type")
        config.addAllowedHeader("Authorization")
        config.addAllowedHeader("x-api-key")

        config.addAllowedOrigin("*")

        config.addExposedHeader("Location")
        config.addExposedHeader("Content-Location")
        config.allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        // Create the interceptor and register it
        interceptorService.registerInterceptor(CorsInterceptor(config))

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
