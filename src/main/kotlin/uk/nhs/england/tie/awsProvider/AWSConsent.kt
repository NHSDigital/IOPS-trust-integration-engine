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
class AWSConsent(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                 @Qualifier("R4") val ctx: FhirContext,
                 val fhirServerProperties: FHIRServerProperties,
                 val awsOrganization: AWSOrganization,
                 val awsPractitionerRole: AWSPractitionerRole,
                 val awsPractitioner: AWSPractitioner,
                 val awsPatient: AWSPatient,
                 val awsEncounter: AWSEncounter,
                 val awsDocumentReference: AWSDocumentReference,
                 val awsBundleProvider : AWSBundle,
                 val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdate(newConsent: Consent, bundle: Bundle?): Consent? {
        var awsBundle: Bundle? = null
        if (!newConsent.hasIdentifier()) throw UnprocessableEntityException("Consent has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newConsent.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("Consent has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(Consent::class.java)
                    .where(
                        Consent.IDENTIFIER.exactly()
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

        if (newConsent.hasPatient()) {
            if (newConsent.patient.hasReference() && bundle != null) {
                val patient = awsPatient.get(newConsent.patient, bundle)
                if (patient != null) awsBundleProvider.updateReference(newConsent.patient, patient.identifierFirstRep, patient )
            } else
                if (newConsent.patient.hasIdentifier()) {
                    val patient = awsPatient.get(newConsent.patient.identifier)
                    if (patient != null) awsBundleProvider.updateReference(newConsent.patient, patient.identifierFirstRep, patient )
                }
        }
        if (newConsent.hasOrganization() && bundle != null) {
            for (performer in newConsent.organization) {
                if (performer.resource != null)
                    if ((performer.resource is Organization)) {
                        val organization = awsOrganization.get(performer, bundle)
                        if (organization != null) awsBundleProvider.updateReference(performer, organization.identifierFirstRep,organization )
                    }
            }
        }
        if (newConsent.hasPerformer() && bundle != null) {
            for (performer in newConsent.performer) {
                if (performer.resource != null)
                    if (performer.resource is PractitionerRole) {
                        val practitionerRole = awsPractitionerRole.get(performer, bundle)
                        if (practitionerRole != null) awsBundleProvider.updateReference(performer, practitionerRole.identifierFirstRep,practitionerRole )
                    } else if ((performer.resource is Practitioner)) {
                        val practitioner = awsPractitioner.get(performer, bundle)
                        if (practitioner != null) awsBundleProvider.updateReference(performer, practitioner.identifierFirstRep,practitioner )
                    }
                    else if ((performer.resource is Patient)) {
                        val patient = awsPatient.get(performer, bundle)
                        if (patient != null) awsBundleProvider.updateReference(performer, patient.identifierFirstRep,patient )
                    }
                    else if ((performer.resource is Organization)) {
                        val organization = awsOrganization.get(performer, bundle)
                        if (organization != null) awsBundleProvider.updateReference(performer, organization.identifierFirstRep,organization )
                    }
            }
        }

        // This v3esquw data should have been processed into propoer resources so remove
        newConsent.contained = ArrayList()

        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Consent
        ) {
            val oldConsent = awsBundle.entryFirstRep.resource as Consent
            // Dont update for now - just return aws Consent
            return update(oldConsent, newConsent)!!.resource as Consent
        } else {
            return create(newConsent)!!.resource as Consent
        }
    }

    public fun search(identifier: Identifier): Consent? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Consent::class.java)
                    .where(
                        Consent.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Consent
    }

    private fun update(oldConsent: Consent, newConsent: Consent): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newConsent.identifier) {
            var found = false
            for (awsidentifier in oldConsent.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                oldConsent.addIdentifier(identifier)
                changed = true
            }
        }

        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(oldConsent)
        var retry = 3
        while (retry > 0) {
            try {
                newConsent.id = oldConsent.idElement.value
                response = awsClient!!.update().resource(newConsent).withId(oldConsent.id).execute()
                log.info("AWS Consent updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(newConsent, AuditEvent.AuditEventAction.C)
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

    private fun create(newConsent: Consent): MethodOutcome? {

        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newConsent)
                    .execute()
                val oldConsent = response.resource as Consent
                val auditEvent = awsAuditEvent.createAudit(oldConsent, AuditEvent.AuditEventAction.C)
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
