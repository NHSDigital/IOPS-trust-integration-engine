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
class AWSObservation(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                     @Qualifier("R4") val ctx: FhirContext,
                     val fhirServerProperties: FHIRServerProperties,
                     val awsOrganization: AWSOrganization,
                     val awsBundleProvider : AWSBundle,
                     val awsPractitionerRole: AWSPractitionerRole,
                     val awsPractitioner: AWSPractitioner,
                     val awsPatient: AWSPatient,
                     val awsEncounter: AWSEncounter,
                     val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdate(newObservation: Observation, bundle: Bundle?): Observation? {
        var awsBundle: Bundle? = null
        if (!newObservation.hasIdentifier()) throw UnprocessableEntityException("Observation has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newObservation.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("Observation has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(Observation::class.java)
                    .where(
                        Observation.IDENTIFIER.exactly()
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

        if (newObservation.hasSubject()) {
            if (newObservation.subject.hasReference() && bundle != null) {
                val patient = awsPatient.get(newObservation.subject, bundle)
                if (patient != null) awsBundleProvider.updateReference(newObservation.subject, patient.identifierFirstRep,patient)
            } else
                if (newObservation.subject.hasIdentifier()) {
                    val patient = awsPatient.get(newObservation.subject.identifier)
                    if (patient != null) awsBundleProvider.updateReference(newObservation.subject, patient.identifierFirstRep,patient)
                }
        }
        if (newObservation.hasEncounter()) {
            if (newObservation.encounter.hasReference() && bundle != null) {
                val encounter = awsEncounter.get(newObservation.encounter, bundle)
                if (encounter != null) awsBundleProvider.updateReference(newObservation.encounter, encounter.identifierFirstRep,encounter)
            } else
                if (newObservation.encounter.hasIdentifier()) {
                    val encounter = awsEncounter.get(newObservation.encounter.identifier)
                    if (encounter != null) awsBundleProvider.updateReference(newObservation.encounter, encounter.identifierFirstRep,encounter)
                }
        }
        if (newObservation.hasDerivedFrom()) {
            for (derived in newObservation.derivedFrom) {
                if (derived.resource != null && derived.resource is Observation) {
                    val derivedObservation = createUpdate(derived.resource as Observation, bundle)
                    if (derivedObservation != null) awsBundleProvider.updateReference(derived, derivedObservation.identifierFirstRep,derivedObservation)
                }
            }
        }
        if (newObservation.hasPerformer() && bundle != null) {
            for (performer in newObservation.performer) {
                if (performer.resource != null) {
                    if (performer.resource is PractitionerRole) {
                        val practitionerRole = awsPractitionerRole.get(performer,bundle)
                        if (practitionerRole != null) awsBundleProvider.updateReference(performer, practitionerRole.identifierFirstRep, practitionerRole)
                    }
                    if (performer.resource is Practitioner) {
                        val practitioner = awsPractitioner.get(performer,bundle)
                        if (practitioner != null) awsBundleProvider.updateReference(performer, practitioner.identifierFirstRep, practitioner)
                    }
                    if (performer.resource is Organization) {
                        val organisation = awsOrganization.get(performer,bundle)
                        if (organisation != null) awsBundleProvider.updateReference(performer, organisation.identifierFirstRep, organisation)
                    }
                } else {
                    if (performer.hasIdentifier()) {
                        if (performer.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                            performer.identifier.system.equals(FhirSystems.NHS_GMP_NUMBER)) {
                            val dr = awsPractitioner.get(performer.identifier)
                            if (dr != null) {
                                awsBundleProvider.updateReference(performer, dr.identifierFirstRep, dr)
                            }
                        }
                        if (performer.identifier.system.equals(FhirSystems.ODS_CODE)) {
                                val organisation = awsOrganization.get(performer.identifier)
                                if (organisation != null) awsBundleProvider.updateReference(performer, organisation.identifierFirstRep, organisation)
                            }
                    }
                }
            }

        }
        // This v3esquw data should have been processed into propoer resources so remove
        newObservation.contained = ArrayList()

        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Observation
        ) {
            val observation = awsBundle.entryFirstRep.resource as Observation
            // Dont update for now - just return aws Observation
            return update(observation, newObservation)!!.resource as Observation
        } else {
            return create(newObservation)!!.resource as Observation
        }
    }

    public fun get(identifier: Identifier): Observation? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Observation::class.java)
                    .where(
                        Observation.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Observation
    }

    private fun update(observation: Observation, newObservation: Observation): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newObservation.identifier) {
            var found = false
            for (awsidentifier in observation.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                observation.addIdentifier(identifier)
                changed = true
            }
        }

        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(observation)
        var retry = 3
        while (retry > 0) {
            try {
                newObservation.id = observation.idElement.value
                response = awsClient!!.update().resource(newObservation).withId(observation.id).execute()
                log.info("AWS Observation updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(observation, AuditEvent.AuditEventAction.C)
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

    private fun create(newObservation: Observation): MethodOutcome? {

        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newObservation)
                    .execute()
                val observation = response.resource as Observation
                val auditEvent = awsAuditEvent.createAudit(observation, AuditEvent.AuditEventAction.C)
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

    fun transform(newObservation : Observation): Observation {
        val bundle : Bundle? = null
        if (newObservation.hasSubject()) {
                if (newObservation.subject.hasIdentifier()) {
                    val patient = awsPatient.get(newObservation.subject.identifier)
                    if (patient != null) awsBundleProvider.updateReference(newObservation.subject, patient.identifierFirstRep,patient)
                }
        }
        if (newObservation.hasEncounter()) {
                if (newObservation.encounter.hasIdentifier()) {
                    val encounter = awsEncounter.get(newObservation.encounter.identifier)
                    if (encounter != null) awsBundleProvider.updateReference(newObservation.encounter, encounter.identifierFirstRep,encounter)
                }
        }
        if (newObservation.hasDerivedFrom()) {
            for (derived in newObservation.derivedFrom) {
                if (derived.resource != null && derived.hasIdentifier()) {
                    val observation = get(derived.identifier)
                    if (observation != null) awsBundleProvider.updateReference(derived, observation.identifierFirstRep,observation)
                }
            }
        }
        if (newObservation.hasPerformer()) {
            for (performer in newObservation.performer) {
                    if (performer.hasIdentifier()) {
                        if (performer.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                            performer.identifier.system.equals(FhirSystems.NHS_GMP_NUMBER)) {
                            val dr = awsPractitioner.get(performer.identifier)
                            if (dr != null) {
                                awsBundleProvider.updateReference(performer, dr.identifierFirstRep, dr)
                            }
                        }
                        if (performer.identifier.system.equals(FhirSystems.ODS_CODE)) {
                            val organisation = awsOrganization.get(performer.identifier)
                            if (organisation != null) awsBundleProvider.updateReference(performer, organisation.identifierFirstRep, organisation)
                        }
                    }
            }

        }
        return newObservation
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

                /*
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
