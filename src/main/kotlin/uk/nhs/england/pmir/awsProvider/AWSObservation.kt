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
import java.util.*

@Component
class AWSObservation(val messageProperties: MessageProperties, val awsClient: IGenericClient,
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


    fun createUpdate(newObservation: Observation, bundle: Bundle?): Observation? {
        var awsBundle: Bundle? = null
        if (!newObservation.hasIdentifier()) throw UnprocessableEntityException("Observation has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newObservation.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("Observation has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(Observation::class.java)
                    .where(
                        Observation.IDENTIFIER.exactly()
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

        if (newObservation.hasSubject()) {
            if (newObservation.subject.hasReference() && bundle != null) {
                val patient = awsPatient.get(newObservation.subject, bundle)
                if (patient != null) awsBundleProvider.updateReference(newObservation.subject, patient.identifierFirstRep,patient)
            } else
                if (newObservation.subject.hasIdentifier()) {
                    val patient = awsPatient.get(newObservation.subject.identifier)
                    if (patient != null) awsBundleProvider.updateReference(newObservation.subject, patient.identifierFirstRep,patient)
                }
        }
        if (newObservation.hasPerformer() && bundle != null) {
            for (performer in newObservation.performer) {
                if (performer.resource != null) {
                    if (performer.resource is PractitionerRole) {
                        val practitionerRole = awsPractitionerRole.get(performer,bundle)
                        if (practitionerRole != null) awsBundleProvider.updateReference(performer, practitionerRole.identifierFirstRep, practitionerRole)
                    }
                    if (performer.resource is Practitioner) {
                        val practitioner = awsPractitioner.get(performer,bundle)
                        if (practitioner != null) awsBundleProvider.updateReference(performer, practitioner.identifierFirstRep, practitioner)
                    }
                    if (performer.resource is Organization) {
                        val organisation = awsOrganization.get(performer,bundle)
                        if (organisation != null) awsBundleProvider.updateReference(performer, organisation.identifierFirstRep, organisation)
                    }
                }
            }

        }
        // This v3esquw data should have been processed into propoer resources so remove
        newObservation.contained = ArrayList()

        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Observation
        ) {
            val observation = awsBundle.entryFirstRep.resource as Observation
            // Dont update for now - just return aws Observation
            return updateObservation(observation, newObservation)!!.resource as Observation
        } else {
            return createObservation(newObservation)!!.resource as Observation
        }
    }

    public fun getObservation(identifier: Identifier): Observation? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Observation::class.java)
                    .where(
                        Observation.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Observation
    }

    private fun updateObservation(observation: Observation, newObservation: Observation): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newObservation.identifier) {
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
                newObservation.id = observation.idElement.value
                response = awsClient!!.update().resource(newObservation).withId(observation.id).execute()
                log.info("AWS Observation updated " + response.resource.idElement.value)
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

    private fun createObservation(newObservation: Observation): MethodOutcome? {

        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newObservation)
                    .execute()
                val observation = response.resource as Observation
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
}
