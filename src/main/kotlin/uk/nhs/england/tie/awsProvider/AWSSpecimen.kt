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
import java.util.*

@Component
class AWSSpecimen(val messageProperties: MessageProperties, val awsClient: IGenericClient,
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


    fun createUpdate(newSpecimen: Specimen, bundle: Bundle?): Specimen? {
        var awsBundle: Bundle? = null
        if (!newSpecimen.hasIdentifier()) throw UnprocessableEntityException("Specimen has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newSpecimen.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("Specimen has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(Specimen::class.java)
                    .where(
                        Specimen.IDENTIFIER.exactly()
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

        if (newSpecimen.hasSubject()) {
            if (newSpecimen.subject.hasReference() && bundle != null) {
                val patient = awsPatient.get(newSpecimen.subject, bundle)
                if (patient != null) awsBundleProvider.updateReference(newSpecimen.subject, patient.identifierFirstRep,patient)
            } else
                if (newSpecimen.subject.hasIdentifier()) {
                    val patient = awsPatient.get(newSpecimen.subject.identifier)
                    if (patient != null) awsBundleProvider.updateReference(newSpecimen.subject, patient.identifierFirstRep,patient)
                }
        }

        // This v3esquw data should have been processed into propoer resources so remove
        newSpecimen.contained = ArrayList()

        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Specimen
        ) {
            val observation = awsBundle.entryFirstRep.resource as Specimen
            // Dont update for now - just return aws Specimen
            return updateSpecimen(observation, newSpecimen)!!.resource as Specimen
        } else {
            return createSpecimen(newSpecimen)!!.resource as Specimen
        }
    }

    public fun getSpecimen(identifier: Identifier): Specimen? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Specimen::class.java)
                    .where(
                        Specimen.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Specimen
    }

    private fun updateSpecimen(observation: Specimen, newSpecimen: Specimen): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newSpecimen.identifier) {
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
                newSpecimen.id = observation.idElement.value
                response = awsClient!!.update().resource(newSpecimen).withId(observation.id).execute()
                log.info("AWS Specimen updated " + response.resource.idElement.value)
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

    private fun createSpecimen(newSpecimen: Specimen): MethodOutcome? {

        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newSpecimen)
                    .execute()
                val observation = response.resource as Specimen
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
