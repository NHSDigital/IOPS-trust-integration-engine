package uk.nhs.england.pmir.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient


import org.hl7.fhir.r4.model.*

import org.hl7.fhir.r4.model.CommunicationRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.pmir.configuration.FHIRServerProperties
import uk.nhs.england.pmir.configuration.MessageProperties

import java.util.*

@Component
class AWSCommunicationRequest (val messageProperties: MessageProperties, val awsClient: IGenericClient,
               //sqs: AmazonSQS?,
                               @Qualifier("R4") val ctx: FhirContext,
                               val fhirServerProperties: FHIRServerProperties,
                               val awsPatient: AWSPatient,
                               val awsOrganization: AWSOrganization,
                               val awsPractitioner: AWSPractitioner,
                               val awsBundleProvider: AWSBundle,
                               val awsAuditEvent: AWSAuditEvent) {


    private val log = LoggerFactory.getLogger("FHIRAudit")


   
    fun create(newCommunicationRequest: CommunicationRequest): MethodOutcome? {
        var response: MethodOutcome? = null

        if (newCommunicationRequest.hasRequester()) {
            if (newCommunicationRequest.requester.hasIdentifier()) {
                val awsOrganization = awsOrganization.get(newCommunicationRequest.requester.identifier)
                if (awsOrganization != null)   awsBundleProvider.updateReference(newCommunicationRequest.requester,awsOrganization.identifierFirstRep,awsOrganization)

            }
        }
        if (newCommunicationRequest.hasRecipient()) {
            for (reference in newCommunicationRequest.recipient)
            if (reference.hasIdentifier()) {
                val awsPatient = awsPatient.get(reference.identifier)
                if (awsPatient != null) {
                    awsBundleProvider.updateReference(reference,awsPatient.identifierFirstRep,awsPatient)
                } else {
                    val awsOrganization = awsOrganization.get(reference.identifier)
                    if (awsOrganization != null) {
                        awsBundleProvider.updateReference(reference,awsOrganization.identifierFirstRep,awsOrganization)
                    } else {
                        val awsPractitioner = awsPractitioner.get(reference.identifier)
                        if (awsPractitioner != null) {
                            awsBundleProvider.updateReference(reference,awsPractitioner.identifierFirstRep,awsPractitioner)
                        }
                    }
                }
            }
        }

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newCommunicationRequest)
                    .execute()
                val communicationRequest = response.resource as CommunicationRequest
                val auditEvent = awsAuditEvent.createAudit(communicationRequest, AuditEvent.AuditEventAction.C)
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
