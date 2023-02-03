package uk.nhs.england.pmir.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.pmir.configuration.FHIRServerProperties
import uk.nhs.england.pmir.configuration.MessageProperties
import uk.nhs.england.pmir.util.FhirSystems
import java.util.*

@Component
class AWSAppointment(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                     @Qualifier("R4") val ctx: FhirContext,
                     val fhirServerProperties: FHIRServerProperties,
                     val awsOrganization: AWSOrganization,
                     val awsPractitioner: AWSPractitioner,
                     val awsPatient: AWSPatient,
                     val awsBundleProvider: AWSBundle,
                     val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdate(newAppointment: Appointment): Appointment? {
        var awsBundle: Bundle? = null
        if (!newAppointment.hasIdentifier()) throw UnprocessableEntityException("Appointment has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newAppointment.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("Appointment has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(Appointment::class.java)
                    .where(
                        Appointment.IDENTIFIER.exactly()
                            .systemAndCode(nhsIdentifier.system, nhsIdentifier.value)
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

        for (participant in newAppointment.participant) {
            if (participant.hasActor() && participant.actor.hasIdentifier()) {
                if (participant.actor.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                    participant.actor.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)) {
                    val dr = awsPractitioner.get(participant.actor.identifier)
                    if (dr != null) {
                        awsBundleProvider.updateReference(participant.actor,dr.identifierFirstRep,dr)
                    }
                }
                if (participant.actor.identifier.system.equals(FhirSystems.NHS_NUMBER)) {
                    val patient = awsPatient.get(participant.actor.identifier)
                    if (patient != null) {
                        awsBundleProvider.updateReference(participant.actor,patient.identifierFirstRep,patient)
                    }
                }
            }
        }

       if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Appointment
        ) {
            val Appointment = awsBundle.entryFirstRep.resource as Appointment
            // Dont update for now - just return aws Appointment
            return updateAppointment(Appointment, newAppointment)!!.resource as Appointment
        } else {
            return createAppointment(newAppointment)!!.resource as Appointment
        }
    }

    private fun updateAppointment(Appointment: Appointment, newAppointment: Appointment): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newAppointment.identifier) {
            var found = false
            for (awsidentifier in Appointment.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                Appointment.addIdentifier(identifier)
                changed = true
            }
        }
        // May need to check status history already contains this history

        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(Appointment)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient!!.update().resource(newAppointment).withId(Appointment.id).execute()
                log.info("AWS Appointment updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(Appointment, AuditEvent.AuditEventAction.C)
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

    private fun createAppointment(newAppointment: Appointment): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newAppointment)
                    .execute()
                val Appointment = response.resource as Appointment
                val auditEvent = awsAuditEvent.createAudit(Appointment, AuditEvent.AuditEventAction.C)
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
