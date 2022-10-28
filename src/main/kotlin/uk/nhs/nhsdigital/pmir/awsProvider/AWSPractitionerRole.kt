package uk.nhs.nhsdigital.pmir.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.pmir.configuration.FHIRServerProperties
import uk.nhs.nhsdigital.pmir.configuration.MessageProperties

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
                    return createPractitionerRole(practitionerRole,bundle)?.resource as PractitionerRole
                } else return awsPractitionerRole
            }
        } else if (reference.hasIdentifier()) {
            return get(reference.identifier)
        }
        return null
    }
    fun createPractitionerRole(newPractitionerRole: PractitionerRole, bundle: Bundle): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null
        if (newPractitionerRole.hasPractitioner()) {
            val practitioner = awsPractitioner.get(newPractitionerRole.practitioner,bundle)
            if (practitioner != null) newPractitionerRole.practitioner.reference = "Practitioner/" + practitioner.idElement.idPart
        }
        if (newPractitionerRole.hasOrganization()) {
            val organization = awsOrganization.get(newPractitionerRole.organization,bundle)
            if (organization != null) newPractitionerRole.organization.reference = "Organization/" + organization.idElement.idPart
        }
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
}
