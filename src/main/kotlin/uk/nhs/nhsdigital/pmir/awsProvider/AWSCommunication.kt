package uk.nhs.nhsdigital.pmir.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Organization
import org.hl7.fhir.r4.model.CommunicationRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.pmir.configuration.FHIRServerProperties
import uk.nhs.nhsdigital.pmir.configuration.MessageProperties
import uk.nhs.nhsdigital.pmir.util.FhirSystems
import java.util.*

@Component
class AWSCommunication (val messageProperties: MessageProperties, val awsClient: IGenericClient,
               //sqs: AmazonSQS?,
                        @Qualifier("R4") val ctx: FhirContext,
                        val fhirServerProperties: FHIRServerProperties,
                        val awsBundleProvider: AWSBundle,
                        val awsAuditEvent: AWSAuditEvent) {


    private val log = LoggerFactory.getLogger("FHIRAudit")


   
    fun createCommunication(newCommunicationRequest: CommunicationRequest): MethodOutcome? {

        var response: MethodOutcome? = null
        var newCommunication = Communication()

        newCommunication.status = Communication.CommunicationStatus.COMPLETED

        for (payloadR in newCommunicationRequest.payload) {
            var payload = Communication.CommunicationPayloadComponent()
            payload.setContent(payloadR.content)
            newCommunication.payload.add(payload)
        }
        newCommunication.recipient = newCommunicationRequest.recipient
        newCommunication.sender = newCommunicationRequest.sender
        newCommunication.received = Date()
        newCommunication.sent = Date()
        newCommunication.subject= newCommunicationRequest.subject

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newCommunication)
                    .execute()
                val communication = response.resource as Communication
                val auditEvent = awsAuditEvent.createAudit(communication, AuditEvent.AuditEventAction.C)
                awsAuditEvent.writeAWS(auditEvent)
                break
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return response
    }


}
