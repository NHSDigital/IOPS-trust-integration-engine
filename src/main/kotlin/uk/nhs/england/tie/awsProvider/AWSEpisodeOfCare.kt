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
import java.util.*

@Component
class AWSEpisodeOfCare(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                       @Qualifier("R4") val ctx: FhirContext,
                       val fhirServerProperties: FHIRServerProperties,
                       val awsOrganization: AWSOrganization,
                       val awsPractitioner: AWSPractitioner,
                       val awsPatient: AWSPatient,
                       val awsCareTeam: AWSCareTeam,
                       val awsCondition: AWSCondition,
                       val awsServiceRequest: AWSServiceRequest,
                       val awsBundleProvider: AWSBundle,
                       val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")

    public fun get(identifier: Identifier): EpisodeOfCare? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(EpisodeOfCare::class.java)
                    .where(
                        EpisodeOfCare.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as EpisodeOfCare
    }
    public fun get(reference: Reference, bundle: Bundle): EpisodeOfCare? {
        var awsEpisodeOfCare : EpisodeOfCare? = null
        if (reference.hasReference()) {

            val encounter = awsBundleProvider.findResource(bundle, "EpisodeOfCare", reference.reference) as EpisodeOfCare?
            if (encounter != null) {
                for ( identifier in encounter.identifier) {
                    awsEpisodeOfCare = get(identifier)
                    if (awsEpisodeOfCare != null) {
                        break;
                    }
                }
                if (awsEpisodeOfCare == null) {
                    return createUpdate(encounter)
                } else return awsEpisodeOfCare
            }
        } else if (reference.hasIdentifier()) {
            return get(reference.identifier)
        }
        return null
    }

    fun createUpdate(newEpisodeOfCare: EpisodeOfCare): EpisodeOfCare? {
        var awsBundle: Bundle? = null
        if (!newEpisodeOfCare.hasIdentifier()) throw UnprocessableEntityException("EpisodeOfCare has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newEpisodeOfCare.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("EpisodeOfCare has no identifier")
        var retry = 3
        while (retry > 0) {
            try {
                awsBundle = awsClient!!.search<IBaseBundle>().forResource(EpisodeOfCare::class.java)
                    .where(
                        EpisodeOfCare.IDENTIFIER.exactly()
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
        if (newEpisodeOfCare.hasManagingOrganization() && newEpisodeOfCare.managingOrganization.hasIdentifier()) {
            val organisation = awsOrganization.get(newEpisodeOfCare.managingOrganization.identifier)
            if (organisation != null) awsBundleProvider.updateReference(newEpisodeOfCare.managingOrganization, organisation.identifierFirstRep, organisation)
        }
        if (newEpisodeOfCare.hasPatient() && newEpisodeOfCare.patient.hasIdentifier()) {
            val patient = awsPatient.get(newEpisodeOfCare.patient.identifier)
            if (patient != null) awsBundleProvider.updateReference(newEpisodeOfCare.patient, patient.identifierFirstRep, patient)
        }
        if (newEpisodeOfCare.hasReferralRequest()) {
            for (referral in newEpisodeOfCare.referralRequest) {
                if (referral.hasIdentifier()) {
                    val serviceRequest = awsServiceRequest.get(referral.identifier)
                    if (serviceRequest != null) awsBundleProvider.updateReference(
                        referral,
                        serviceRequest.identifierFirstRep,
                        serviceRequest
                    )
                }
            }
        }
        for (participant in newEpisodeOfCare.team) {
            if (participant.hasIdentifier()) {
                val dr = awsCareTeam.search(TokenParam()
                    .setSystem(participant.identifier.system)
                    .setValue(participant.identifier.value))
                if (dr.size>0 ) {
                    awsBundleProvider.updateReference(participant,dr.get(0).identifierFirstRep,dr.get(0))
                }
            }
        }

        if (newEpisodeOfCare.hasCareManager() && newEpisodeOfCare.careManager.hasIdentifier()) {
            if (newEpisodeOfCare.careManager.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                newEpisodeOfCare.careManager.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)) {
                val dr = awsPractitioner.get(newEpisodeOfCare.careManager.identifier)
                if (dr != null) {
                    awsBundleProvider.updateReference(newEpisodeOfCare.careManager,dr.identifierFirstRep,dr)
                }
            }
        }
        if (newEpisodeOfCare.hasDiagnosis()) {
            for(diagnosis in newEpisodeOfCare.diagnosis) {
                if (diagnosis.hasCondition()) {
                    if (diagnosis.condition.hasType() && diagnosis.condition.hasIdentifier()) {
                        when(diagnosis.condition.type) {
                            "Condition" -> {
                                val condition = awsCondition.get(diagnosis.condition.identifier)
                                if (condition != null) awsBundleProvider.updateReference(
                                    diagnosis.condition,
                                    condition.identifierFirstRep,
                                    condition
                                )
                                break
                            }
                        }
                    }
                }
            }
        }


       if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is EpisodeOfCare
        ) {
            val encounter = awsBundle.entryFirstRep.resource as EpisodeOfCare
            // Dont update for now - just return aws EpisodeOfCare
            return update(encounter, newEpisodeOfCare)!!.resource as EpisodeOfCare
        } else {
            return create(newEpisodeOfCare)!!.resource as EpisodeOfCare
        }
    }

    private fun update(encounter: EpisodeOfCare, newEpisodeOfCare: EpisodeOfCare): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newEpisodeOfCare.identifier) {
            var found = false
            for (awsidentifier in encounter.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                encounter.addIdentifier(identifier)
                changed = true
            }
        }
        // May need to check status history already contains this history
        // TODO
        encounter.addStatusHistory()
            .setStatus(encounter.status)
            .setPeriod(encounter.period)
       // newEpisodeOfCare.statusHistory = encounter.statusHistory

        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(encounter)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient!!.update().resource(newEpisodeOfCare).withId(encounter.id).execute()
                log.info("AWS EpisodeOfCare updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(encounter, AuditEvent.AuditEventAction.C)
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

    private fun create(newEpisodeOfCare: EpisodeOfCare): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newEpisodeOfCare)
                    .execute()
                val encounter = response.resource as EpisodeOfCare
                val auditEvent = awsAuditEvent.createAudit(encounter, AuditEvent.AuditEventAction.C)
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

    fun transform(newEpisodeOfCare: EpisodeOfCare): Resource? {
        if (newEpisodeOfCare.hasManagingOrganization() && newEpisodeOfCare.managingOrganization.hasIdentifier()) {
            val organisation = awsOrganization.get(newEpisodeOfCare.managingOrganization.identifier)
            if (organisation != null) awsBundleProvider.updateReference(newEpisodeOfCare.managingOrganization, organisation.identifierFirstRep, organisation)
        }
        if (newEpisodeOfCare.hasPatient() && newEpisodeOfCare.patient.hasIdentifier()) {
            val patient = awsPatient.get(newEpisodeOfCare.patient.identifier)
            if (patient != null) awsBundleProvider.updateReference(newEpisodeOfCare.patient, patient.identifierFirstRep, patient)
        }
        if (newEpisodeOfCare.hasReferralRequest()) {
            for (referral in newEpisodeOfCare.referralRequest) {
                if (referral.hasIdentifier()) {
                    val serviceRequest = awsServiceRequest.get(referral.identifier)
                    if (serviceRequest != null) awsBundleProvider.updateReference(
                        referral,
                        serviceRequest.identifierFirstRep,
                        serviceRequest
                    )
                }
            }
        }
        for (participant in newEpisodeOfCare.team) {
            if (participant.hasIdentifier()) {
                val dr = awsCareTeam.search(TokenParam()
                    .setSystem(participant.identifier.system)
                    .setValue(participant.identifier.value))
                if (dr.size>0 ) {
                    awsBundleProvider.updateReference(participant,dr.get(0).identifierFirstRep,dr.get(0))
                }
            }
        }

        if (newEpisodeOfCare.hasCareManager() && newEpisodeOfCare.careManager.hasIdentifier()) {
            if (newEpisodeOfCare.careManager.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                newEpisodeOfCare.careManager.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)) {
                val dr = awsPractitioner.get(newEpisodeOfCare.careManager.identifier)
                if (dr != null) {
                    awsBundleProvider.updateReference(newEpisodeOfCare.careManager,dr.identifierFirstRep,dr)
                }
            }
        }
        if (newEpisodeOfCare.hasDiagnosis()) {
            for(diagnosis in newEpisodeOfCare.diagnosis) {
                if (diagnosis.hasCondition()) {
                    if (diagnosis.condition.hasType() && diagnosis.condition.hasIdentifier()) {
                        when(diagnosis.condition.type) {
                            "Condition" -> {
                                val condition = awsCondition.get(diagnosis.condition.identifier)
                                if (condition != null) awsBundleProvider.updateReference(
                                    diagnosis.condition,
                                    condition.identifierFirstRep,
                                    condition
                                )
                                break
                            }
                        }
                    }
                }
            }
        }
        return newEpisodeOfCare
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
