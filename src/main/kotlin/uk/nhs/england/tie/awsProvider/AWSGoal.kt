package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.Delete
import ca.uhn.fhir.rest.annotation.IdParam
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
import javax.servlet.http.HttpServletRequest

@Component
class AWSGoal(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
              @Qualifier("R4") val ctx: FhirContext,
              val fhirServerProperties: FHIRServerProperties,
              val awsAuditEvent: AWSAuditEvent,
              val awsOrganization: AWSOrganization,
              val awsPractitioner: AWSPractitioner,
              val awsPatient: AWSPatient,
              val awsCondition: AWSCondition,
              val awsMedicationRequest: AWSMedicationRequest,
              val awsObservation: AWSObservation,
              val awsServiceRequest: AWSServiceRequest,
              val awsBundleProvider: AWSBundle
) {

    private val log = LoggerFactory.getLogger("FHIRAudit")
    fun get(identifier: Identifier) : Goal? {
        val results = search(TokenParam().setSystem(identifier.system).setValue(identifier.value))
        if (results.size > 1) return results.get(0)
        return null
    }
    public fun search(identifier: TokenParam) : List<Goal> {
        var resources = mutableListOf<Goal>()
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Goal::class.java)
                    .where(
                        Goal.IDENTIFIER.exactly().code(identifier.value)
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
                if (entry.hasResource() && entry.resource is Goal) resources.add(entry.resource as Goal)
            }
        }
        return resources
    }
    fun create(newGoal: Goal, bundle: Bundle?): MethodOutcome? {
        var response: MethodOutcome? = null
        val duplicateCheck = search(TokenParam().setValue(newGoal.identifierFirstRep.value))
        if (duplicateCheck.size>0) throw UnprocessableEntityException("A Goal with this identifier already exists.")
        val updatedGoal = transform(newGoal, bundle)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(updatedGoal)
                    .execute()
                val Goal = response.resource as Goal
                val auditEvent = awsAuditEvent.createAudit(Goal, AuditEvent.AuditEventAction.C)
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
    fun update(newGoal: Goal, bundle: Bundle?,theId: IdType?): MethodOutcome? {
        var response: MethodOutcome? = null
        val updatedGoal = transform(newGoal, bundle)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .update()
                    .resource(updatedGoal)
                    .withId(theId)
                    .execute()
                val Goal = response.resource as Goal
                val auditEvent = awsAuditEvent.createAudit(Goal, AuditEvent.AuditEventAction.U)
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
    fun transform(newGoal: Goal, bundle: Bundle?): Goal {


        if (newGoal.hasSubject() && newGoal.subject.hasIdentifier()) {
            val patient = awsPatient.get(newGoal.subject.identifier)
            if (patient != null) awsBundleProvider.updateReference(newGoal.subject, patient.identifierFirstRep, patient)
        }
        if (newGoal.hasExpressedBy()) {
            if (newGoal.expressedBy.hasIdentifier()) {
                if (newGoal.expressedBy.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER) ||
                    newGoal.expressedBy.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)
                ) {
                    val dr = awsPractitioner.get(newGoal.expressedBy.identifier)
                    if (dr != null) {
                        awsBundleProvider.updateReference(newGoal.expressedBy, dr.identifierFirstRep, dr)
                    }
                }
                if (newGoal.expressedBy.identifier.system.equals(FhirSystems.NHS_NUMBER)) {
                    val org = awsPatient.get(newGoal.expressedBy.identifier)
                    if (org != null) {
                        awsBundleProvider.updateReference(newGoal.expressedBy, org.identifierFirstRep, org)
                    }
                }
            }
        }
        if (newGoal.hasAddresses()) {
            for (reference in newGoal.addresses) {
                if (reference.hasType() && reference.hasIdentifier()) {
                    when(reference.type) {
                        "Condition" -> {
                            val condition = awsCondition.get(reference.identifier)
                            if (condition != null) awsBundleProvider.updateReference(
                                reference,
                                condition.identifierFirstRep,
                                condition
                            )
                            break
                        }
                        "MedicationRequest" -> {
                            val condition = awsMedicationRequest.get(reference.identifier)
                            if (condition != null) awsBundleProvider.updateReference(
                                reference,
                                condition.identifierFirstRep,
                                condition
                            )
                            break
                        }
                        "ServiceRequest" -> {
                            val condition = awsServiceRequest.get(reference.identifier)
                            if (condition != null) awsBundleProvider.updateReference(
                                reference,
                                condition.identifierFirstRep,
                                condition
                            )
                            break
                        }
                        "Observation" -> {
                            val condition = awsObservation.get(reference.identifier)
                            if (condition != null) awsBundleProvider.updateReference(
                                reference,
                                condition.identifierFirstRep,
                                condition
                            )
                            break
                        }
                    }
                }
            }
        }
        return newGoal
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
