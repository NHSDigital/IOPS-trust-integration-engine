package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.configuration.MessageProperties
import uk.nhs.england.tie.util.FhirSystems

@Component
class AWSCareTeam(val messageProperties: MessageProperties, val awsClient: IGenericClient,
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
   
    fun create(newCareTeam: CareTeam, bundle: Bundle?): MethodOutcome? {
        var response: MethodOutcome? = null
        if (newCareTeam.hasSubject() && newCareTeam.subject.hasIdentifier()) {
            val patient = awsPatient.get(newCareTeam.subject.identifier)
            if (patient != null) awsBundleProvider.updateReference(newCareTeam.subject, patient.identifierFirstRep, patient)
        }

       if (newCareTeam.hasParticipant()) {
           for (participant in newCareTeam.participant) {
               if (participant.hasMember() && participant.member.hasIdentifier()) {
                   if (participant.member.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER) ||
                       participant.member.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)
                   ) {
                       val dr = awsPractitioner.get(participant.member.identifier)
                       if (dr != null) {
                           awsBundleProvider.updateReference(participant.member, dr.identifierFirstRep, dr)
                       }
                   }
                   if (participant.member.identifier.system.equals(FhirSystems.ODS_CODE)) {
                       val org = awsOrganization.get(participant.member.identifier)
                       if (org != null) {
                           awsBundleProvider.updateReference(participant.member, org.identifierFirstRep, org)
                       }
                   }
               }
               if (participant.hasOnBehalfOf() && participant.onBehalfOf.hasIdentifier()) {
                   if (participant.onBehalfOf.identifier.system.equals(FhirSystems.ODS_CODE)) {
                       val org = awsOrganization.get(participant.onBehalfOf.identifier)
                       if (org != null) {
                           awsBundleProvider.updateReference(participant.onBehalfOf, org.identifierFirstRep, org)
                       }
                   }
               }
           }
       }
        if (newCareTeam.hasManagingOrganization()) {
            for (reference in newCareTeam.managingOrganization) {
                if (reference.hasIdentifier()) {
                    if (reference.identifier.system.equals(FhirSystems.ODS_CODE)) {
                        val org = awsOrganization.get(reference.identifier)
                        if (org != null) {
                            awsBundleProvider.updateReference(reference, org.identifierFirstRep, org)
                        }
                    }
                }
            }
        }


        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newCareTeam)
                    .execute()
                val careTeam = response.resource as CareTeam
                val auditEvent = awsAuditEvent.createAudit(careTeam, AuditEvent.AuditEventAction.C)
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
