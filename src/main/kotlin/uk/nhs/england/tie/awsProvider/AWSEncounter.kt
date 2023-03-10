package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
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
class AWSEncounter(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                   @Qualifier("R4") val ctx: FhirContext,
                   val fhirServerProperties: FHIRServerProperties,
                   val awsOrganization: AWSOrganization,
                   val awsPractitioner: AWSPractitioner,
                   val awsPatient: AWSPatient,
                   val awsBundleProvider: AWSBundle,
                   val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")

    public fun get(identifier: Identifier): Encounter? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Encounter::class.java)
                    .where(
                        Encounter.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Encounter
    }
    public fun get(reference: Reference, bundle: Bundle): Encounter? {
        var awsEncounter : Encounter? = null
        if (reference.hasReference()) {

            val encounter = awsBundleProvider.findResource(bundle, "Encounter", reference.reference) as Encounter?
            if (encounter != null) {
                for ( identifier in encounter.identifier) {
                    awsEncounter = get(identifier)
                    if (awsEncounter != null) {
                        break;
                    }
                }
                if (awsEncounter == null) {
                    return createUpdate(encounter)
                } else return awsEncounter
            }
        } else if (reference.hasIdentifier()) {
            return get(reference.identifier)
        }
        return null
    }

    fun createUpdate(newEncounter: Encounter): Encounter? {
        var awsBundle: Bundle? = null
        if (!newEncounter.hasIdentifier()) throw UnprocessableEntityException("Encounter has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newEncounter.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("Encounter has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(Encounter::class.java)
                    .where(
                        Encounter.IDENTIFIER.exactly()
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
        if (newEncounter.hasServiceProvider() && newEncounter.getServiceProvider().hasIdentifier()) {
            val organisation = awsOrganization.get(newEncounter.serviceProvider.identifier)
            if (organisation != null) awsBundleProvider.updateReference(newEncounter.serviceProvider, organisation.identifierFirstRep, organisation)
        }
        if (newEncounter.hasSubject() && newEncounter.subject.hasIdentifier()) {
            val patient = awsPatient.get(newEncounter.subject.identifier)
            if (patient != null) awsBundleProvider.updateReference(newEncounter.subject, patient.identifierFirstRep, patient)
        }
        for (participant in newEncounter.participant) {
            if (participant.hasIndividual() && participant.individual.hasIdentifier()) {
                if (participant.individual.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                    participant.individual.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)) {
                    val dr = awsPractitioner.get(participant.individual.identifier)
                    if (dr != null) {
                        awsBundleProvider.updateReference(participant.individual,dr.identifierFirstRep,dr)
                    }
                }
                if (participant.individual.identifier.system.equals(FhirSystems.ODS_CODE)) {
                    val org = awsOrganization.get(participant.individual.identifier)
                    if (org != null) {
                        awsBundleProvider.updateReference(participant.individual,org.identifierFirstRep,org)
                    }
                }
            }
        }

       if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Encounter
        ) {
            val encounter = awsBundle.entryFirstRep.resource as Encounter
            // Dont update for now - just return aws Encounter
            return update(encounter, newEncounter)!!.resource as Encounter
        } else {
            return create(newEncounter)!!.resource as Encounter
        }
    }

    private fun update(encounter: Encounter, newEncounter: Encounter): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newEncounter.identifier) {
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
       // newEncounter.statusHistory = encounter.statusHistory

        if (encounter.hasLocation() && newEncounter.hasLocation()) {
            // Try to amend the location history
            var lastlocation : Encounter.EncounterLocationComponent? = null
            for (location in encounter.location) {
                if (location.hasLocation() && location.location.hasIdentifier()) {
                    lastlocation = location
                }
            }
            // add new location if not present in old locations
            if (lastlocation != null) {
                if (!lastlocation.location.identifier.value.equals(newEncounter.locationFirstRep.location.identifier.value)) {
                    encounter.location.add(newEncounter.locationFirstRep) }
                else {
                    lastlocation.period = newEncounter.locationFirstRep.period
                }
            }
            newEncounter.location = encounter.location
        }
        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(encounter)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient!!.update().resource(newEncounter).withId(encounter.id).execute()
                log.info("AWS Encounter updated " + response.resource.idElement.value)
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

    private fun create(newEncounter: Encounter): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newEncounter)
                    .execute()
                val encounter = response.resource as Encounter
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

    fun transform(newEncounter: Encounter): Resource? {
        if (newEncounter.hasServiceProvider() && newEncounter.getServiceProvider().hasIdentifier()) {
            val organisation = awsOrganization.get(newEncounter.serviceProvider.identifier)
            if (organisation != null) awsBundleProvider.updateReference(newEncounter.serviceProvider, organisation.identifierFirstRep, organisation)
        }
        if (newEncounter.hasSubject() && newEncounter.subject.hasIdentifier()) {
            val patient = awsPatient.get(newEncounter.subject.identifier)
            if (patient != null) awsBundleProvider.updateReference(newEncounter.subject, patient.identifierFirstRep, patient)
        }
        for (participant in newEncounter.participant) {
            if (participant.hasIndividual() && participant.individual.hasIdentifier()) {
                if (participant.individual.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                    participant.individual.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)) {
                    val dr = awsPractitioner.get(participant.individual.identifier)
                    if (dr != null) {
                        awsBundleProvider.updateReference(participant.individual,dr.identifierFirstRep,dr)
                    }
                }
                if (participant.individual.identifier.system.equals(FhirSystems.ODS_CODE)) {
                    val org = awsOrganization.get(participant.individual.identifier)
                    if (org != null) {
                        awsBundleProvider.updateReference(participant.individual,org.identifierFirstRep,org)
                    }
                }
            }
        }
        return newEncounter
    }
}
