package uk.nhs.england.pmir.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.pmir.configuration.FHIRServerProperties
import uk.nhs.england.pmir.configuration.MessageProperties
import java.util.*

@Component
class AWSMedicationDispense(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                            @Qualifier("R4") val ctx: FhirContext,
                            val fhirServerProperties: FHIRServerProperties,
                            val awsOrganization: AWSOrganization,
                            val awsBundleProvider : AWSBundle,
                            val awsPractitionerRole: AWSPractitionerRole,
                            val awsPatient: AWSPatient,
                            val awsMedicationRequest: AWSMedicationRequest,
                            val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdate(newMedicationDispense: MedicationDispense, bundle: Bundle?): MedicationDispense? {
        var awsBundle: Bundle? = null
        if (!newMedicationDispense.hasIdentifier()) throw UnprocessableEntityException("MedicationDispense has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newMedicationDispense.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("MedicationDispense has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(MedicationDispense::class.java)
                    .where(
                        MedicationDispense.IDENTIFIER.exactly()
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

        if (newMedicationDispense.hasSubject()) {
            if (newMedicationDispense.subject.hasReference() && bundle != null) {
                val patient = awsPatient.getPatient(newMedicationDispense.subject, bundle)
                if (patient != null) awsBundleProvider.updateReference(newMedicationDispense.subject,
                    patient.identifierFirstRep,patient)
            } else
                if (newMedicationDispense.subject.hasIdentifier()) {
                    val patient = awsPatient.getPatient(newMedicationDispense.subject.identifier)
                    if (patient != null)
                        awsBundleProvider.updateReference(newMedicationDispense.subject,
                            patient.identifierFirstRep,patient)

                }
        }
        if (newMedicationDispense.hasPerformer() && bundle != null) {
            for (performer in newMedicationDispense.performer) {
                if (performer.hasActor()) {
                    val practitionerRole = awsPractitionerRole.get(performer.actor, bundle)
                    if (practitionerRole != null) {
                        awsBundleProvider.updateReference(performer.actor,
                            practitionerRole.identifierFirstRep,practitionerRole)
                        performer.actor.resource = null
                    }
                }
            }
        }
        if (newMedicationDispense.hasAuthorizingPrescription() && newMedicationDispense.authorizingPrescriptionFirstRep.hasIdentifier()) {
            val medicationRequest = awsMedicationRequest.getMedicationRequest(newMedicationDispense.authorizingPrescriptionFirstRep.identifier)
            if (medicationRequest != null) {
                awsBundleProvider.updateReference(newMedicationDispense.authorizingPrescriptionFirstRep,
                    medicationRequest.identifierFirstRep,medicationRequest)

            }
        }
        if (newMedicationDispense.hasAuthorizingPrescription()
            && newMedicationDispense.authorizingPrescriptionFirstRep.resource != null
            && newMedicationDispense.authorizingPrescriptionFirstRep.resource is MedicationRequest) {
            val domainResource = newMedicationDispense.authorizingPrescriptionFirstRep.resource as MedicationRequest
            if (domainResource.hasIdentifier()) {
                val medicationRequest =
                    awsMedicationRequest.getMedicationRequest(domainResource.identifierFirstRep)
                if (medicationRequest != null) {
                    awsBundleProvider.updateReference(newMedicationDispense.authorizingPrescriptionFirstRep,
                        medicationRequest.identifierFirstRep,medicationRequest)
                }
            }

        }
        // Get rid of the v3esque contained data
        newMedicationDispense.contained = ArrayList()

        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is MedicationDispense
        ) {
            val medicationDispense = awsBundle.entryFirstRep.resource as MedicationDispense
            // Dont update for now - just return aws MedicationDispense
            return updateMedicationDispense(medicationDispense, newMedicationDispense)!!.resource as MedicationDispense
        } else {
            return createMedicationDispense(newMedicationDispense)!!.resource as MedicationDispense
        }
    }

    private fun updateMedicationDispense(medicationDispense: MedicationDispense, newMedicationDispense: MedicationDispense): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newMedicationDispense.identifier) {
            var found = false
            for (awsidentifier in medicationDispense.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                medicationDispense.addIdentifier(identifier)
                changed = true
            }
        }

        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(medicationDispense)
        var retry = 3
        while (retry > 0) {
            try {
                newMedicationDispense.id = medicationDispense.idElement.value
                response = awsClient!!.update().resource(newMedicationDispense).withId(medicationDispense.id).execute()
                log.info("AWS MedicationDispense updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(medicationDispense, AuditEvent.AuditEventAction.C)
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

    private fun createMedicationDispense(newMedicationDispense: MedicationDispense): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newMedicationDispense)
                    .execute()
                val medicationDispense = response.resource as MedicationDispense
                val auditEvent = awsAuditEvent.createAudit(medicationDispense, AuditEvent.AuditEventAction.C)
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
