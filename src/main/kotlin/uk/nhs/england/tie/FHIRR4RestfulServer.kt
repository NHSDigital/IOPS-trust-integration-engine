package uk.nhs.england.tie

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import com.amazonaws.services.sqs.AmazonSQS
import org.springframework.beans.factory.annotation.Qualifier
import uk.nhs.england.tie.provider.BinaryProvider
import uk.nhs.england.tie.provider.DocumentReferenceProvider
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.configuration.MessageProperties
import uk.nhs.england.tie.provider.*
import uk.nhs.england.tie.interceptor.AWSAuditEventLoggingInterceptor
import uk.nhs.england.tie.interceptor.CapabilityStatementInterceptor
import uk.nhs.england.tie.interceptor.ValidationInterceptor
import java.util.*
import jakarta.servlet.annotation.WebServlet

@WebServlet("/FHIR/R4/*", loadOnStartup = 1)
class FHIRR4RestfulServer(
    @Qualifier("R4") fhirContext: FhirContext,
    public val fhirServerProperties: FHIRServerProperties,
    val messageProperties: MessageProperties,
    public val processMessageProvider: ProcessMessageProvider,
    val transactionProvider: TransactionProvider,
    val patientProvider: PatientProvider,
    val patientSearchProvider: PatientSearchProvider,

   // val subscriptionProvider: SubscriptionProvider,

    val questionnaireResponseProvider: QuestionnaireResponseProvider,
    val questionnaireResponsePlainProvider: QuestionnaireResponsePlainProvider,
    private val questionnaireProvider: QuestionnaireProvider,
    private val questionnairePlainProvider: QuestionnairePlainProvider,


    val communicationRequestProvider: CommunicationRequestProvider,
    val communicationProvider: CommunicationProvider,
    val communicationPlainProvider: CommunicationPlainProvider,
    val serviceRequestProvider: ServiceRequestProvider,
    val taskProvider: TaskProvider,
    val serviceRequestPlainProvider: ServiceRequestPlainProvider,
    val taskPlainProvider: TaskPlainProvider,

    val diagnosticReportPlainProvider: DiagnosticReportPlainProvider,
    val diagnosticReportProvider: DiagnosticReportProvider,
    val observationSearchProvider: ObservationSearchProvider,
    val observationProvider: ObservationProvider,

    val medicationRequestProvider: MedicationRequestProvider,

    val binaryProvider: BinaryProvider,
    val documentReferenceProvider: DocumentReferenceProvider,

    val careTeamProvider: CareTeamProvider,
    val careTeamPlainProvider: CareTeamPlainProvider,
    val encounterProvider: EncounterProvider,
    val episodeOfCareProvider: EpisodeOfCareProvider,
    val conditionProvider: ConditionProvider,


    val carePlanPlainProvider: CarePlanPlainProvider,
    val carePlanProvider: CarePlanProvider,
    val goalProvider: GoalProvider,
    val goalPlainProvider: GoalPlainProvider,
    val activityDefinitionProvider: ActivityDefinitionProvider,
    val activityDefinitionPlainProvider: ActivityDefinitionPlainProvider,
    val planDefinitionProvider: PlanDefinitionProvider,
    val planDefinitionPlainProvider: PlanDefinitionPlainProvider,
    val valueSetProvider: ValueSetProvider,
    val sqs : AmazonSQS

    ) : RestfulServer(fhirContext) {

    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        registerProvider(processMessageProvider)
        registerProvider(transactionProvider)

        registerProvider(patientProvider)
        registerProvider(patientSearchProvider)
        registerProvider(episodeOfCareProvider)
        registerProvider(encounterProvider)
        registerProvider(conditionProvider)
        registerProvider(medicationRequestProvider)


       // registerProvider(subscriptionProvider)

        registerProvider(communicationRequestProvider)
        registerProvider(communicationProvider)
        registerProvider(communicationPlainProvider)

        registerProvider(questionnaireResponseProvider)
        registerProvider(questionnaireResponsePlainProvider)
        registerProvider(questionnaireProvider)
        registerProvider(questionnairePlainProvider)
        registerProvider(observationSearchProvider)
        registerProvider(observationProvider)
        registerProvider(valueSetProvider)

        registerProvider(binaryProvider)
        registerProvider(documentReferenceProvider)

        registerProvider(taskProvider)
        registerProvider(taskPlainProvider)
        registerProvider(serviceRequestProvider)
        registerProvider(serviceRequestPlainProvider)
        registerProvider(diagnosticReportProvider)
        registerProvider(diagnosticReportPlainProvider)

        registerProvider(careTeamProvider)
        registerProvider(careTeamPlainProvider)

        registerProvider(carePlanProvider)
        registerProvider(carePlanPlainProvider)
        registerProvider(goalProvider)
        registerProvider(goalPlainProvider)
        registerProvider(activityDefinitionProvider)
        registerProvider(activityDefinitionPlainProvider)
        registerProvider(planDefinitionProvider)
        registerProvider(planDefinitionPlainProvider)


        registerInterceptor(CapabilityStatementInterceptor(this.fhirContext,fhirServerProperties))


        val awsAuditEventLoggingInterceptor =
            AWSAuditEventLoggingInterceptor(
                this.fhirContext,
                fhirServerProperties,
                messageProperties,
                sqs
            )
        interceptorService.registerInterceptor(awsAuditEventLoggingInterceptor)

        val validationInterceptor = ValidationInterceptor(fhirContext,messageProperties)
        interceptorService.registerInterceptor(validationInterceptor)

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
