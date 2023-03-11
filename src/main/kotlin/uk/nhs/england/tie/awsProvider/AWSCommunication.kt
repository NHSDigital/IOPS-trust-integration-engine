package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.param.TokenParam
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.CommunicationRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.configuration.MessageProperties
import uk.nhs.england.tie.util.FhirSystems
import java.util.*

@Component
class AWSCommunication (val messageProperties: MessageProperties, val awsClient: IGenericClient,
               //sqs: AmazonSQS?,
                        @Qualifier("R4") val ctx: FhirContext,
                        val fhirServerProperties: FHIRServerProperties,
                        val awsBundleProvider: AWSBundle,
                        val awsPatient: AWSPatient,
                        val awsPractitioner: AWSPractitioner,
                        val awsOrganization: AWSOrganization,
                        val awsAuditEvent: AWSAuditEvent) {


    private val log = LoggerFactory.getLogger("FHIRAudit")


   
    fun create(newCommunicationRequest: CommunicationRequest): MethodOutcome? {

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
    fun delete(theId: IdType): MethodOutcome? {
        var response: MethodOutcome? = null
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .delete()
                    .resourceById(theId)
                    .execute()

                /* TODO
                  val auditEvent = awsAuditEvent.createAudit(storedQuestionnaire, AuditEvent.AuditEventAction.D)
                  awsAuditEvent.writeAWS(auditEvent)
                  */
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
    public fun get(identifier: Identifier): Communication? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Communication::class.java)
                    .where(
                        Communication.IDENTIFIER.exactly()
                            .systemAndCode(identifier.system, identifier.value)
                    )
                    .returnBundle(Bundle::class.java)
                    .execute()
                break
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        if (bundle == null || !bundle.hasEntry()) return null
        return bundle.entryFirstRep.resource as Communication
    }
    fun update(newCommunication: Communication): MethodOutcome? {
        var response: MethodOutcome? = null
        val communication = transform(newCommunication)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient!!.update().resource(communication).withId(communication.id).execute()
                log.info("AWS Communication updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(newCommunication, AuditEvent.AuditEventAction.C)
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

    fun create(newCommunication: Communication): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null
        val communication = transform(newCommunication)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(communication)
                    .execute()
                val encounter = response.resource as Communication
                val auditEvent = awsAuditEvent.createAudit(encounter, AuditEvent.AuditEventAction.C)
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

    fun transform(newCommunication: Communication): Resource {
        if (newCommunication.hasSubject() && newCommunication.subject.hasIdentifier()) {
            val patient = awsPatient.get(newCommunication.subject.identifier)
            if (patient != null) awsBundleProvider.updateReference(newCommunication.subject, patient.identifierFirstRep, patient)
        }
        if (newCommunication.hasSender() && newCommunication.sender.hasIdentifier()) {
            if (newCommunication.sender.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                newCommunication.sender.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)) {
                val dr = awsPractitioner.get(newCommunication.sender.identifier)
                if (dr != null) {
                    awsBundleProvider.updateReference(newCommunication.sender,dr.identifierFirstRep,dr)
                }
            }
            if (newCommunication.sender.identifier.system.equals(FhirSystems.ODS_CODE)) {
                val org = awsOrganization.get(newCommunication.sender.identifier)
                if (org != null) {
                    awsBundleProvider.updateReference(newCommunication.sender,org.identifierFirstRep,org)
                }
            }
            if (newCommunication.sender.identifier.system.equals(FhirSystems.NHS_NUMBER)) {
                val patient = awsPatient.get(newCommunication.sender.identifier)
                if (patient != null) {
                    awsBundleProvider.updateReference(newCommunication.sender,patient.identifierFirstRep,patient)
                }
            }
        }

        for (participant in newCommunication.recipient) {
            if (participant.hasIdentifier()) {
                if (participant.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                    participant.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)) {
                    val dr = awsPractitioner.get(participant.identifier)
                    if (dr != null) {
                        awsBundleProvider.updateReference(participant,dr.identifierFirstRep,dr)
                    }
                }
                if (participant.identifier.system.equals(FhirSystems.ODS_CODE)) {
                    val org = awsOrganization.get(participant.identifier)
                    if (org != null) {
                        awsBundleProvider.updateReference(participant,org.identifierFirstRep,org)
                    }
                }
                if (participant.identifier.system.equals(FhirSystems.NHS_NUMBER)) {
                    val patient = awsPatient.get(participant.identifier)
                    if (patient != null) {
                        awsBundleProvider.updateReference(participant,patient.identifierFirstRep,patient)
                    }
                }
            }
        }

        if (newCommunication.hasInResponseTo() ){
            for (responseTo in newCommunication.inResponseTo) {
                if (responseTo.hasIdentifier()) {
                    val comms = get(responseTo.identifier)
                    if (comms != null) {
                        awsBundleProvider.updateReference(responseTo,comms.identifierFirstRep,comms)
                    }
                }
            }
        }

        return newCommunication
    }
}
