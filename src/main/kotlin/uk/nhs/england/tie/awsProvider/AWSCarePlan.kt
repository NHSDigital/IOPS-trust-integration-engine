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
class AWSCarePlan(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                  @Qualifier("R4") val ctx: FhirContext,
                  val fhirServerProperties: FHIRServerProperties,
                  val awsAuditEvent: AWSAuditEvent,
                  val awsOrganization: AWSOrganization,
                  val awsPractitioner: AWSPractitioner,
                  val awsPatient: AWSPatient,
                  val awsCareTeam: AWSCareTeam,
                  val awsBundleProvider: AWSBundle
) {

    private val log = LoggerFactory.getLogger("FHIRAudit")
    public fun search(identifier: TokenParam) : List<CarePlan> {
        var resources = mutableListOf<CarePlan>()
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(CarePlan::class.java)
                    .where(
                        CarePlan.IDENTIFIER.exactly().code(identifier.value)
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
                if (entry.hasResource() && entry.resource is CarePlan) resources.add(entry.resource as CarePlan)
            }
        }
        return resources
    }
    fun create(newCarePlan: CarePlan, bundle: Bundle?): MethodOutcome? {

        val duplicateCheck = search(TokenParam().setValue(newCarePlan.identifierFirstRep.value))
        if (duplicateCheck.size>0) throw UnprocessableEntityException("A CarePlan with this identifier already exists.")

        var response: MethodOutcome? = null
        if (newCarePlan.hasSubject() && newCarePlan.subject.hasIdentifier()) {
            val patient = awsPatient.get(newCarePlan.subject.identifier)
            if (patient != null) awsBundleProvider.updateReference(newCarePlan.subject, patient.identifierFirstRep, patient)
        }

       if (newCarePlan.hasContributor()) {
           for (participant in newCarePlan.contributor) {
               if (participant.hasIdentifier()) {
                   if (participant.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER) ||
                       participant.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)
                   ) {
                       val dr = awsPractitioner.get(participant.identifier)
                       if (dr != null) {
                           awsBundleProvider.updateReference(participant, dr.identifierFirstRep, dr)
                       }
                   }
                   if (participant.identifier.system.equals(FhirSystems.ODS_CODE)) {
                       val org = awsOrganization.get(participant.identifier)
                       if (org != null) {
                           awsBundleProvider.updateReference(participant, org.identifierFirstRep, org)
                       }
                   }
               }
           }
       }
        if (newCarePlan.hasAuthor()) {
            if (newCarePlan.author.hasIdentifier()) {
                if (newCarePlan.author.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER) ||
                    newCarePlan.author.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)
                ) {
                    val dr = awsPractitioner.get(newCarePlan.author.identifier)
                    if (dr != null) {
                        awsBundleProvider.updateReference(newCarePlan.author, dr.identifierFirstRep, dr)
                    }
                }
                if (newCarePlan.author.identifier.system.equals(FhirSystems.ODS_CODE)) {
                    val org = awsOrganization.get(newCarePlan.author.identifier)
                    if (org != null) {
                        awsBundleProvider.updateReference(newCarePlan.author, org.identifierFirstRep, org)
                    }
                }
            }
        }
        for (participant in newCarePlan.careTeam) {
            if (participant.hasIdentifier()) {
                val dr = awsCareTeam.search(TokenParam()
                    .setSystem(participant.identifier.system)
                    .setValue(participant.identifier.value))
                if (dr.size>0 ) {
                    awsBundleProvider.updateReference(participant,dr.get(0).identifierFirstRep,dr.get(0))
                }
            }
        }


        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newCarePlan)
                    .execute()
                val carePlan = response.resource as CarePlan
                val auditEvent = awsAuditEvent.createAudit(carePlan, AuditEvent.AuditEventAction.C)
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
