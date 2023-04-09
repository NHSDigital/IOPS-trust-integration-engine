package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.configuration.MessageProperties
import uk.nhs.england.tie.util.FhirSystems


@Component
class AWSActivityDefinition(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                            @Qualifier("R4") val ctx: FhirContext,
                            val fhirServerProperties: FHIRServerProperties,
                            val awsAuditEvent: AWSAuditEvent,
                            val awsOrganization: AWSOrganization,
                            val awsPractitioner: AWSPractitioner,
                            val awsPatient: AWSPatient,
                            val awsBundleProvider: AWSBundle
) {

    private val log = LoggerFactory.getLogger("FHIRAudit")

    fun get(identifier: Identifier) : ActivityDefinition? {
        val results = search(TokenParam().setSystem(identifier.system).setValue(identifier.value))
        if (results.size > 1) return results.get(0)
        return null
    }
    public fun search(identifier: TokenParam) : List<ActivityDefinition> {
        var resources = mutableListOf<ActivityDefinition>()
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(ActivityDefinition::class.java)
                    .where(
                        ActivityDefinition.IDENTIFIER.exactly().code(identifier.value)
                    )
                    .returnBundle(Bundle::class.java)
                    .execute()
                 break;
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        if (bundle!=null && bundle.hasEntry()) {
            for (entry in bundle.entry) {
                if (entry.hasResource() && entry.resource is ActivityDefinition) resources.add(entry.resource as ActivityDefinition)
            }
        }
        return resources
    }
    fun create(newActivityDefinition: ActivityDefinition, bundle: Bundle?): MethodOutcome? {
        var response: MethodOutcome? = null
        val duplicateCheck = search(TokenParam().setValue(newActivityDefinition.identifierFirstRep.value))
        if (duplicateCheck.size>0) throw UnprocessableEntityException("A ActivityDefinition with this identifier already exists.")
        val updatedActivityDefinition = transform(newActivityDefinition, bundle)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(updatedActivityDefinition)
                    .execute()
                val activityDefinition = response.resource as ActivityDefinition
                val auditEvent = awsAuditEvent.createAudit(activityDefinition, AuditEvent.AuditEventAction.C)
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
    fun update(newActivityDefinition: ActivityDefinition, bundle: Bundle?,theId: IdType?): MethodOutcome? {
        var response: MethodOutcome? = null
        val updatedActivityDefinition = transform(newActivityDefinition, bundle)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .update()
                    .resource(updatedActivityDefinition)
                    .withId(theId)
                    .execute()
                val activityDefinition = response.resource as ActivityDefinition
                val auditEvent = awsAuditEvent.createAudit(activityDefinition, AuditEvent.AuditEventAction.U)
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
    fun transform(newActivityDefinition: ActivityDefinition, bundle: Bundle?): ActivityDefinition {
        // Definitional resource
        return newActivityDefinition
    }
    fun delete(theId: IdType): MethodOutcome? {
        var response: MethodOutcome? = null
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .delete()
                    .resourceById(theId)
                    .execute()

              /* TODO
                val auditEvent = awsAuditEvent.createAudit(storedQuestionnaire, AuditEvent.AuditEventAction.D)
                awsAuditEvent.writeAWS(auditEvent)
                */
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
