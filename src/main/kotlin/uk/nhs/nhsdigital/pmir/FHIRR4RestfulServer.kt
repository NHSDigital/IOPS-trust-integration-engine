package uk.nhs.nhsdigital.pmir

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import org.springframework.beans.factory.annotation.Qualifier
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
    val questionnaireProvider: QuestionnaireProvider
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

        val awsAuditEventLoggingInterceptor =
            AWSAuditEventLoggingInterceptor(
                this.fhirContext,
                fhirServerProperties
            )
        interceptorService.registerInterceptor(awsAuditEventLoggingInterceptor)


        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
