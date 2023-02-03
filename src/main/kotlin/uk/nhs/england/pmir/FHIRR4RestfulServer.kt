package uk.nhs.england.pmir

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import org.springframework.beans.factory.annotation.Qualifier
import uk.nhs.england.pmir.configuration.FHIRServerProperties
import uk.nhs.england.pmir.provider.*
import uk.nhs.england.pmir.interceptor.AWSAuditEventLoggingInterceptor
import uk.nhs.england.pmir.interceptor.CapabilityStatementInterceptor
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

        registerInterceptor(CapabilityStatementInterceptor(this.fhirContext,fhirServerProperties))


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
