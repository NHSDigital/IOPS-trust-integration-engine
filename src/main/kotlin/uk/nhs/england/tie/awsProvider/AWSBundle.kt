package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.configuration.MessageProperties

@Component
class AWSBundle(val messageProperties: MessageProperties, val awsClient: IGenericClient,
                val awsAuditEvent: AWSAuditEvent,
                @Qualifier("R4") val ctx: FhirContext
) {
    private val log = LoggerFactory.getLogger("FHIRAudit")
    fun transaction(bundle: Bundle): Bundle? {
        var response: Bundle? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .transaction().withBundle(bundle).execute()

                val auditEvent = awsAuditEvent.createAudit(response, AuditEvent.AuditEventAction.C)
                awsAuditEvent.writeAWS(auditEvent)
                return response

            } catch (ex: InvalidRequestException) {
                // do nothing
                log.error(ex.message)
                log.debug(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle))
                retry--
                if (retry == 1) {
                    if (ex.responseBody != null) {
                        try {
                            val operation = ctx.newJsonParser().parseResource(ex.responseBody)
                            if (operation is OperationOutcome && (operation as OperationOutcome).hasIssue()) {
                                throw UnprocessableEntityException(operation.issueFirstRep.diagnostics)
                            }
                        } catch (exNew: Exception) {
                            throw ex
                        }
                    }
                    throw ex
                }


            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)

                retry--
                if (retry == 0) {
                    log.debug(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle))
                    throw ex
                }
            }
        }
        return response
    }
    fun filterResources(bundle: Bundle, resourceType : String): List<Resource> {
        val resource = ArrayList<Resource>()

        for (entry in bundle.entry) {
            if (entry.hasResource() && entry.resource.resourceType.name.equals(resourceType)) {
                resource.add(entry.resource)
            }
        }
        return resource
    }
    fun findResource(bundle: Bundle, resourceType : String, reference : String): Resource? {
        val resource = ArrayList<Resource>()

        for (entry in bundle.entry) {
            if (entry.hasResource()) {
                if (entry.resource.resourceType.name.equals(resourceType)
                    && entry.fullUrl.equals(reference))
                {
                    return entry.resource
                }
                if (entry.resource is DomainResource) {
                    val domainResource : DomainResource = entry.resource as DomainResource
                    if (domainResource.hasContained()) {
                        for( contained in domainResource.contained) {
                            if (contained.id.equals(reference)) return contained
                        }
                    }
                }
            }
        }
        return null
    }

    fun updateReference(reference : Reference, identifier: Identifier?, resource : DomainResource) {
        // Ensure contained resource is removed
        if (reference.resource != null) reference.resource = null
        reference.reference = resource.javaClass.simpleName + "/" + resource.idElement.idPart
        if (!reference.hasIdentifier() && identifier != null) {
            identifier.extension = ArrayList<Extension>() // Get rid of extensions
            reference.identifier = identifier
        }
    }
}
