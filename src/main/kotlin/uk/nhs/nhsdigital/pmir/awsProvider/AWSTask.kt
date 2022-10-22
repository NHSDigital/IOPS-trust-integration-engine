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
class AWSTask(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
              @Qualifier("R4") val ctx: FhirContext,
              val fhirServerProperties: FHIRServerProperties,
              val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdateAWSTask(newTask: Task): Task? {
        var awsBundle: Bundle? = null
        if (!newTask.hasIdentifier()) throw UnprocessableEntityException("Task has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newTask.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("Task has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(Task::class.java)
                    .where(
                        Task.IDENTIFIER.exactly()
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


        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Task
        ) {
            val task = awsBundle.entryFirstRep.resource as Task
            // Dont update for now - just return aws Task
           // updateTask(task, newTask)!!.resource as Task
            return null
        } else {
            return createTask(newTask)!!.resource as Task
        }
    }

    private fun updateTask(task: Task, newTask: Task): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false


        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(task)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient!!.update().resource(newTask).withId(task.id).execute()
                log.info("AWS Task updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(task, AuditEvent.AuditEventAction.C)
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

    private fun createTask(newTask: Task): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newTask)
                    .execute()
                val task = response.resource as Task
                val auditEvent = awsAuditEvent.createAudit(task, AuditEvent.AuditEventAction.C)
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
