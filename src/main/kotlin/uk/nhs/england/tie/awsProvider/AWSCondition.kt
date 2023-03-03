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
class AWSCondition(val messageProperties: MessageProperties, val awsClient: IGenericClient,
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


    fun createUpdate(newCondition: Condition, bundle: Bundle?): Condition? {
        var awsBundle: Bundle? = null
        if (!newCondition.hasIdentifier()) throw UnprocessableEntityException("Condition has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newCondition.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("Condition has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(Condition::class.java)
                    .where(
                        Condition.IDENTIFIER.exactly()
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

        if (newCondition.hasSubject()) {
            if (newCondition.subject.hasReference() && bundle != null) {
                val patient = awsPatient.get(newCondition.subject, bundle)
                if (patient != null) awsBundleProvider.updateReference(newCondition.subject, patient.identifierFirstRep,patient)
            } else
                if (newCondition.subject.hasIdentifier()) {
                    val patient = awsPatient.get(newCondition.subject.identifier)
                    if (patient != null) awsBundleProvider.updateReference(newCondition.subject, patient.identifierFirstRep,patient)
                }
        }


        if (newCondition.hasAsserter()) {
            var performer = newCondition.asserter

            if (performer.hasIdentifier()) {
                if (performer.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                    performer.identifier.system.equals(FhirSystems.NHS_GMP_NUMBER)) {
                    val dr = awsPractitioner.get(performer.identifier)
                    if (dr != null) {
                        awsBundleProvider.updateReference(performer, dr.identifierFirstRep, dr)
                    }
                }
                if (performer.identifier.system.equals(FhirSystems.ODS_CODE)) {
                        val organisation = awsOrganization.get(performer.identifier)
                        if (organisation != null) awsBundleProvider.updateReference(performer, organisation.identifierFirstRep, organisation)
                    }
            }
        }
        if (newCondition.hasRecorder()) {
            var performer = newCondition.recorder

            if (performer.hasIdentifier()) {
                if (performer.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                    performer.identifier.system.equals(FhirSystems.NHS_GMP_NUMBER)) {
                    val dr = awsPractitioner.get(performer.identifier)
                    if (dr != null) {
                        awsBundleProvider.updateReference(performer, dr.identifierFirstRep, dr)
                    }
                }
                if (performer.identifier.system.equals(FhirSystems.ODS_CODE)) {
                    val organisation = awsOrganization.get(performer.identifier)
                    if (organisation != null) awsBundleProvider.updateReference(performer, organisation.identifierFirstRep, organisation)
                }
            }
        }
        // This v3esquw data should have been processed into propoer resources so remove
        newCondition.contained = ArrayList()

        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Condition
        ) {
            val observation = awsBundle.entryFirstRep.resource as Condition
            // Dont update for now - just return aws Condition
            return update(observation, newCondition)!!.resource as Condition
        } else {
            return create(newCondition)!!.resource as Condition
        }
    }

    public fun get(identifier: Identifier): Condition? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Condition::class.java)
                    .where(
                        Condition.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Condition
    }

    private fun update(observation: Condition, newCondition: Condition): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newCondition.identifier) {
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
                newCondition.id = observation.idElement.value
                response = awsClient!!.update().resource(newCondition).withId(observation.id).execute()
                log.info("AWS Condition updated " + response.resource.idElement.value)
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

    private fun create(newCondition: Condition): MethodOutcome? {

        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newCondition)
                    .execute()
                val observation = response.resource as Condition
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
