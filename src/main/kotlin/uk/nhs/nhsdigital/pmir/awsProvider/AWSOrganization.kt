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
class AWSOrganization(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                      @Qualifier("R4") val ctx: FhirContext,
                      val fhirServerProperties: FHIRServerProperties,
                      val awsAuditEvent: AWSAuditEvent,
                      val awsBundle: AWSBundle
) {

    private val log = LoggerFactory.getLogger("FHIRAudit")
    public fun getOrganization(identifier: Identifier): Organization? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Organization::class.java)
                    .where(
                        Organization.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Organization
    }

    public fun getOrganization(reference: Reference, bundle: Bundle): Organization? {
        var awsOrganization : Organization? = null
        if (reference.hasReference()) {
            val organization = awsBundle.findResource(bundle, "Organization", reference.reference) as Organization
            if (organization != null) {
                for ( identifier in organization.identifier) {
                    awsOrganization = getOrganization(identifier)
                    if (awsOrganization != null) {
                        break;
                    }
                }
                if (awsOrganization == null) {
                    createOrganization(organization,bundle)
                } else return awsOrganization
            }
        } else if (reference.hasIdentifier()) {
            return getOrganization(reference.identifier)
        }
        return null
    }
    fun createOrganization(newOrganization: Organization, bundle: Bundle): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newOrganization)
                    .execute()
                val organization = response.resource as Organization
                val auditEvent = awsAuditEvent.createAudit(organization, AuditEvent.AuditEventAction.C)
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
