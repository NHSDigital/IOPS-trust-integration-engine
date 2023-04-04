package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.param.UriParam
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
class AWSTask(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
              @Qualifier("R4") val ctx: FhirContext,
              val fhirServerProperties: FHIRServerProperties,
              val awsPatient: AWSPatient,
              val awsOrganization: AWSOrganization,
              val awsQuestionnaire: AWSQuestionnaire,
              val awsPractitioner: AWSPractitioner,
              val awsBundleProvider: AWSBundle,
              val awsServiceRequest: AWSServiceRequest,
              val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")

    public fun get(identifier: Identifier): Task? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Task::class.java)
                    .where(
                        Task.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Task
    }
    fun createUpdate(newTask: Task, bundle: Bundle?): Task {
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

        if (newTask.hasRequester()) {
            if (newTask.requester.hasIdentifier()) {
                if (newTask.requester.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                    newTask.requester.identifier.system.equals(FhirSystems.NHS_GMP_NUMBER)) {
                    val dr = awsPractitioner.get(newTask.requester.identifier)
                    if (dr != null) {
                        awsBundleProvider.updateReference(newTask.requester, dr.identifierFirstRep, dr)
                    }
                }
                if (newTask.requester.identifier.system.equals(FhirSystems.ODS_CODE)) {
                    val organisation = awsOrganization.get(newTask.requester.identifier)
                    if (organisation != null) awsBundleProvider.updateReference(newTask.requester, organisation.identifierFirstRep, organisation)
                }

            }
        }
        if (newTask.hasFocus()) {
            if (newTask.focus.hasReference() && newTask.focus.reference.contains("Questionnaire")) {
                val questionnaire = awsQuestionnaire.search(UriParam().setValue(newTask.focus.reference))
                if (questionnaire != null && questionnaire.size>0) {
                    val canonical = newTask.focus.reference
                    awsBundleProvider.updateReference(newTask.focus,questionnaire[0].identifierFirstRep,questionnaire[0])
                    newTask.focus.display = canonical
                }
            }
            if (newTask.focus.hasIdentifier() && newTask.focus.identifier.hasSystem()) {
                if (newTask.focus.identifier.system.equals(FhirSystems.UBRN)) {
                    val serviceRequest = awsServiceRequest.get(newTask.focus.identifier)
                    if (serviceRequest != null ) {
                        val canonical = newTask.focus.reference
                        awsBundleProvider.updateReference(newTask.focus,newTask.focus.identifier,serviceRequest)
                    }
                }
            }
        }
        if (newTask.hasFor()) {
            val reference = newTask.`for`
            if (reference.hasIdentifier()) {
                val awsPatient = awsPatient.get(reference.identifier)
                if (awsPatient != null) {
                    awsBundleProvider.updateReference(reference,awsPatient.identifierFirstRep,awsPatient)
                } else {
                    val awsOrganization = awsOrganization.get(reference.identifier)
                    if (awsOrganization != null) {
                        awsBundleProvider.updateReference(reference,awsOrganization.identifierFirstRep,awsOrganization)
                    } else {
                        val awsPractitioner = awsPractitioner.get(reference.identifier)
                        if (awsPractitioner != null) {
                            awsBundleProvider.updateReference(reference,awsPractitioner.identifierFirstRep,awsPractitioner)
                        }
                    }
                }
            }
        }
        if (newTask.hasOwner()) {
            val reference = newTask.owner
            if (reference.hasIdentifier()) {
                val awsPatient = awsPatient.get(reference.identifier)
                if (awsPatient != null) {
                    awsBundleProvider.updateReference(reference,awsPatient.identifierFirstRep,awsPatient)
                } else {
                    val awsOrganization = awsOrganization.get(reference.identifier)
                    if (awsOrganization != null) {
                        awsBundleProvider.updateReference(reference,awsOrganization.identifierFirstRep,awsOrganization)
                    } else {
                        val awsPractitioner = awsPractitioner.get(reference.identifier)
                        if (awsPractitioner != null) {
                            awsBundleProvider.updateReference(reference,awsPractitioner.identifierFirstRep,awsPractitioner)
                        }
                    }
                }
            }
        }



        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Task
        ) {
            return updateTask(awsBundle.entryFirstRep.resource as Task, newTask)?.resource as Task

        } else {
            return createTask(newTask)?.resource as Task
        }
    }


    fun createUpdate(newTask: Task): MethodOutcome? {
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

        if (newTask.hasRequester()) {
            if (newTask.requester.hasIdentifier()) {
                val awsOrganization = awsOrganization.get(newTask.requester.identifier)
                if (awsOrganization != null)   awsBundleProvider.updateReference(newTask.requester,awsOrganization.identifierFirstRep,awsOrganization)

            }
        }
        if (newTask.hasFocus()) {
            if (newTask.focus.hasReference() && newTask.focus.reference.contains("Questionnaire")) {
                val questionnaire = awsQuestionnaire.search(UriParam().setValue(newTask.focus.reference))
                if (questionnaire != null && questionnaire.size>0) {
                    val canonical = newTask.focus.reference
                    awsBundleProvider.updateReference(newTask.focus,questionnaire[0].identifierFirstRep,questionnaire[0])
                    newTask.focus.display = canonical
                }
            }
        }
        if (newTask.hasFor()) {
            val reference = newTask.`for`
            if (reference.hasIdentifier()) {
                val awsPatient = awsPatient.get(reference.identifier)
                if (awsPatient != null) {
                    awsBundleProvider.updateReference(reference,awsPatient.identifierFirstRep,awsPatient)
                } else {
                    val awsOrganization = awsOrganization.get(reference.identifier)
                    if (awsOrganization != null) {
                        awsBundleProvider.updateReference(reference,awsOrganization.identifierFirstRep,awsOrganization)
                    } else {
                        val awsPractitioner = awsPractitioner.get(reference.identifier)
                        if (awsPractitioner != null) {
                            awsBundleProvider.updateReference(reference,awsPractitioner.identifierFirstRep,awsPractitioner)
                        }
                    }
                }
            }
        }
        if (newTask.hasOwner()) {
            val reference = newTask.owner
            if (reference.hasIdentifier()) {
                val awsPatient = awsPatient.get(reference.identifier)
                if (awsPatient != null) {
                    awsBundleProvider.updateReference(reference,awsPatient.identifierFirstRep,awsPatient)
                } else {
                    val awsOrganization = awsOrganization.get(reference.identifier)
                    if (awsOrganization != null) {
                        awsBundleProvider.updateReference(reference,awsOrganization.identifierFirstRep,awsOrganization)
                    } else {
                        val awsPractitioner = awsPractitioner.get(reference.identifier)
                        if (awsPractitioner != null) {
                            awsBundleProvider.updateReference(reference,awsPractitioner.identifierFirstRep,awsPractitioner)
                        }
                    }
                }
            }
        }



        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Task
        ) {
           return updateTask(awsBundle.entryFirstRep.resource as Task, newTask)
            return null
        } else {
            return createTask(newTask)
        }
    }

    private fun updateTask(task: Task, newTask: Task): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false


        // TODO do change detection
        changed = true;

       // if (!changed) return MethodOutcome().setResource(task)
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

    fun transform(newTask: Task): Resource? {
        if (newTask.hasRequester()) {
            if (newTask.requester.hasIdentifier()) {
                val awsOrganization = awsOrganization.get(newTask.requester.identifier)
                if (awsOrganization != null)   awsBundleProvider.updateReference(newTask.requester,awsOrganization.identifierFirstRep,awsOrganization)

            }
        }
        if (newTask.hasFocus()) {
            if (newTask.focus.hasReference() && newTask.focus.reference.contains("Questionnaire")) {
                val questionnaire = awsQuestionnaire.search(UriParam().setValue(newTask.focus.reference))
                if (questionnaire != null && questionnaire.size>0) {
                    val canonical = newTask.focus.reference
                    awsBundleProvider.updateReference(newTask.focus,questionnaire[0].identifierFirstRep,questionnaire[0])
                    newTask.focus.display = canonical
                }
            }
        }
        if (newTask.hasFor()) {
            val reference = newTask.`for`
            if (reference.hasIdentifier()) {
                val awsPatient = awsPatient.get(reference.identifier)
                if (awsPatient != null) {
                    awsBundleProvider.updateReference(reference,awsPatient.identifierFirstRep,awsPatient)
                } else {
                    val awsOrganization = awsOrganization.get(reference.identifier)
                    if (awsOrganization != null) {
                        awsBundleProvider.updateReference(reference,awsOrganization.identifierFirstRep,awsOrganization)
                    } else {
                        val awsPractitioner = awsPractitioner.get(reference.identifier)
                        if (awsPractitioner != null) {
                            awsBundleProvider.updateReference(reference,awsPractitioner.identifierFirstRep,awsPractitioner)
                        }
                    }
                }
            }
        }
        if (newTask.hasOwner()) {
            val reference = newTask.owner
            if (reference.hasIdentifier()) {
                val awsPatient = awsPatient.get(reference.identifier)
                if (awsPatient != null) {
                    awsBundleProvider.updateReference(reference,awsPatient.identifierFirstRep,awsPatient)
                } else {
                    val awsOrganization = awsOrganization.get(reference.identifier)
                    if (awsOrganization != null) {
                        awsBundleProvider.updateReference(reference,awsOrganization.identifierFirstRep,awsOrganization)
                    } else {
                        val awsPractitioner = awsPractitioner.get(reference.identifier)
                        if (awsPractitioner != null) {
                            awsBundleProvider.updateReference(reference,awsPractitioner.identifierFirstRep,awsPractitioner)
                        }
                    }
                }
            }
        }
        return newTask
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
