package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.configuration.MessageProperties
import uk.nhs.england.tie.util.FhirSystems
import java.util.*

@Component
class AWSSchedule(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                  @Qualifier("R4") val ctx: FhirContext,
                  val fhirServerProperties: FHIRServerProperties,
                  val awsOrganization: AWSOrganization,
                  val awsBundleProvider : AWSBundle,
                  val awsPractitionerRole: AWSPractitionerRole,
                  val awsPractitioner: AWSPractitioner,
                  val awsPatient: AWSPatient,
                  val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdate(newSchedule: Schedule, bundle: Bundle?): Schedule? {
        var awsBundle: Bundle? = null
        if (!newSchedule.hasIdentifier()) throw UnprocessableEntityException("Schedule has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newSchedule.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("Schedule has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(Schedule::class.java)
                    .where(
                        Schedule.IDENTIFIER.exactly()
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

        var schedule = transform(newSchedule) as Schedule

        // This v3esquw data should have been processed into propoer resources so remove
        schedule.contained = ArrayList()

        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Schedule
        ) {
            val observation = awsBundle.entryFirstRep.resource as Schedule
            // Dont update for now - just return aws Schedule
            return updateSchedule(observation, schedule)!!.resource as Schedule
        } else {
            return createSchedule(schedule)!!.resource as Schedule
        }
    }

    public fun get(identifier: Identifier): Schedule? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Schedule::class.java)
                    .where(
                        Schedule.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Schedule
    }

    private fun updateSchedule(observation: Schedule, newSchedule: Schedule): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newSchedule.identifier) {
            var found = false
            for (awsidentifier in observation.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                observation.addIdentifier(identifier)
                changed = true
            }
        }

        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(observation)
        var retry = 3
        while (retry > 0) {
            try {
                newSchedule.id = observation.idElement.value
                response = awsClient!!.update().resource(newSchedule).withId(observation.id).execute()
                log.info("AWS Schedule updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(observation, AuditEvent.AuditEventAction.C)
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

    private fun createSchedule(newSchedule: Schedule): MethodOutcome? {

        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newSchedule)
                    .execute()
                val observation = response.resource as Schedule
                val auditEvent = awsAuditEvent.createAudit(observation, AuditEvent.AuditEventAction.C)
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

    fun transform(newSchedule: Schedule): Resource? {
        if (newSchedule.hasActor()) {
            if (newSchedule.actor.size> 0 ) {
                for (reference in newSchedule.actor) {
                    if (reference.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER) ||
                        reference.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)
                    ) {
                        val dr = awsPractitioner.get(reference.identifier)
                        if (dr != null) {
                            awsBundleProvider.updateReference(reference, dr.identifierFirstRep, dr)
                        }
                    }
                    if (reference.identifier.system.equals(FhirSystems.ODS_CODE)) {
                        val organisation = awsOrganization.get(reference.identifier)
                        if (organisation != null) awsBundleProvider.updateReference(
                            reference,
                            organisation.identifierFirstRep,
                            organisation
                        )
                    }
                }
            }
        }
        return newSchedule
    }
}
