package uk.nhs.nhsdigital.pmir.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.pmir.configuration.FHIRServerProperties
import uk.nhs.nhsdigital.pmir.configuration.MessageProperties
import java.util.*

@Component
class AWSRelatedPerson(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                       @Qualifier("R4") val ctx: FhirContext,
                       val fhirServerProperties: FHIRServerProperties,
                       val awsBundleProvider : AWSBundle,
                       val awsPatient: AWSPatient,
                       val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdateAWSRelatedPerson(newRelatedPerson: RelatedPerson, bundle: Bundle?): RelatedPerson? {
        var awsBundle: Bundle? = null
        if (!newRelatedPerson.hasIdentifier()) throw UnprocessableEntityException("RelatedPerson has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newRelatedPerson.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("RelatedPerson has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(RelatedPerson::class.java)
                    .where(
                        RelatedPerson.IDENTIFIER.exactly()
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

        if (newRelatedPerson.hasPatient()) {
            if (newRelatedPerson.patient.hasReference() && bundle != null) {
                val patient = awsPatient.getPatient(newRelatedPerson.patient, bundle)
                if (patient != null) awsBundleProvider.updateReference(newRelatedPerson.patient, patient.identifierFirstRep,patient)
            } else
                if (newRelatedPerson.patient.hasIdentifier()) {
                    val patient = awsPatient.getPatient(newRelatedPerson.patient.identifier)
                    if (patient != null) awsBundleProvider.updateReference(newRelatedPerson.patient, patient.identifierFirstRep,patient)
                }
        }

        // This v3esquw data should have been processed into propoer resources so remove
        newRelatedPerson.contained = ArrayList()

        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is RelatedPerson
        ) {
            val relatedPerson = awsBundle.entryFirstRep.resource as RelatedPerson
            // Dont update for now - just return aws RelatedPerson
            return updateRelatedPerson(relatedPerson, newRelatedPerson)!!.resource as RelatedPerson
        } else {
            return createRelatedPerson(newRelatedPerson)!!.resource as RelatedPerson
        }
    }

    public fun getRelatedPerson(identifier: Identifier): RelatedPerson? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(RelatedPerson::class.java)
                    .where(
                        RelatedPerson.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as RelatedPerson
    }

    private fun updateRelatedPerson(relatedPerson: RelatedPerson, newRelatedPerson: RelatedPerson): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newRelatedPerson.identifier) {
            var found = false
            for (awsidentifier in relatedPerson.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                relatedPerson.addIdentifier(identifier)
                changed = true
            }
        }

        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(relatedPerson)
        var retry = 3
        while (retry > 0) {
            try {
                newRelatedPerson.id = relatedPerson.idElement.value
                response = awsClient!!.update().resource(newRelatedPerson).withId(relatedPerson.id).execute()
                log.info("AWS RelatedPerson updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(relatedPerson, AuditEvent.AuditEventAction.C)
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

    private fun createRelatedPerson(newRelatedPerson: RelatedPerson): MethodOutcome? {

        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newRelatedPerson)
                    .execute()
                val relatedPerson = response.resource as RelatedPerson
                val auditEvent = awsAuditEvent.createAudit(relatedPerson, AuditEvent.AuditEventAction.C)
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
