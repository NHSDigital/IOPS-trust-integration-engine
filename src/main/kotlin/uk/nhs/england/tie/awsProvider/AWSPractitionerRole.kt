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

@Component
class AWSPractitionerRole(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                          @Qualifier("R4") val ctx: FhirContext,
                          val fhirServerProperties: FHIRServerProperties,
                          val awsBundle: AWSBundle,
                          val awsAuditEvent: AWSAuditEvent,
                          val awsPractitioner: AWSPractitioner,
                          val awsOrganization: AWSOrganization
) {

    private val log = LoggerFactory.getLogger("FHIRAudit")

    public fun get(identifier: Identifier): PractitionerRole? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(PractitionerRole::class.java)
                    .where(
                        PractitionerRole.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as PractitionerRole
    }

    public fun get(reference: Reference, bundle: Bundle): PractitionerRole? {
        var awsPractitionerRole : PractitionerRole? = null
        if (reference.hasReference()) {
            val practitionerRole = awsBundle.findResource(bundle, "PractitionerRole", reference.reference) as PractitionerRole
            if (practitionerRole != null) {
                for ( identifier in practitionerRole.identifier) {
                        awsPractitionerRole = get(identifier)
                        if (awsPractitionerRole != null) {
                            break;
                    }
                }
                if (awsPractitionerRole == null) {
                    return createUpdate(practitionerRole,bundle)
                } else return awsPractitionerRole
            }
        } else if (reference.hasIdentifier()) {
            return get(reference.identifier)
        }
        return null
    }

    fun update(practitionerRole: PractitionerRole, newPactitionerRole: PractitionerRole): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newPactitionerRole.identifier) {
            var found = false
            for (awsidentifier in practitionerRole.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                practitionerRole.addIdentifier(identifier)
                changed = true
            }
        }

        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(practitionerRole)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient!!.update().resource(newPactitionerRole).withId(practitionerRole.id).execute()
                log.info("AWS PractitionerRole updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(practitionerRole, AuditEvent.AuditEventAction.C)
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
    fun create(newPractitionerRole: PractitionerRole): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null


        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newPractitionerRole)
                    .execute()
                val practitionerRole = response.resource as PractitionerRole
                val auditEvent = awsAuditEvent.createAudit(practitionerRole, AuditEvent.AuditEventAction.C)
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


    public fun createUpdate(newPractitionerRole: PractitionerRole, bundle: Bundle): PractitionerRole {
        var awsBundle: Bundle? = null
        var response: MethodOutcome? = null
        var nhsIdentifier: Identifier? = null
        for (identifier in newPractitionerRole.identifier) {
            // This was a NHS Number check but this has been removed to allow to for more flexible demonstrations
            // if (identifier.system == FhirSystems.NHS_NUMBER) {
            nhsIdentifier = identifier
            break
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("PractitionerRole has no NHS Number identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(PractitionerRole::class.java)
                    .where(
                        PractitionerRole.IDENTIFIER.exactly()
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
        if (newPractitionerRole.hasPractitioner()) {
            val practitioner = awsPractitioner.get(newPractitionerRole.practitioner,bundle)
            if (practitioner != null) newPractitionerRole.practitioner.reference = "Practitioner/" + practitioner.idElement.idPart
        }
        if (newPractitionerRole.hasOrganization()) {
            val organization = awsOrganization.get(newPractitionerRole.organization,bundle)
            if (organization != null) newPractitionerRole.organization.reference = "Organization/" + organization.idElement.idPart
        }
        return if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is PractitionerRole
        ) {
            val practitionerRole = awsBundle.entryFirstRep.resource as PractitionerRole
            // Dont update for now - just return aws PractitionerRole
            return update(practitionerRole, newPractitionerRole)!!.resource as PractitionerRole
        } else {
            return create(newPractitionerRole)!!.resource as PractitionerRole
        }
    }


}
