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
class AWSMedicationRequest(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                           @Qualifier("R4") val ctx: FhirContext,
                           val fhirServerProperties: FHIRServerProperties,
                           val awsOrganization: AWSOrganization,
                           val awsBundleProvider : AWSBundle,
                           val awsPractitionerRole: AWSPractitionerRole,
                           val awsPatient: AWSPatient,
                           val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdate(newMedicationRequest: MedicationRequest, bundle: Bundle?): MedicationRequest? {
        var awsBundle: Bundle? = null
        if (!newMedicationRequest.hasIdentifier()) throw UnprocessableEntityException("MedicationRequest has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newMedicationRequest.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("MedicationRequest has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(MedicationRequest::class.java)
                    .where(
                        MedicationRequest.IDENTIFIER.exactly()
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

        if (newMedicationRequest.hasSubject()) {
            if (newMedicationRequest.subject.hasReference() && bundle != null) {
                val patient = awsPatient.getPatient(newMedicationRequest.subject, bundle)
                if (patient != null) awsBundleProvider.updateReference(newMedicationRequest.subject, patient.identifierFirstRep,patient)
            } else
                if (newMedicationRequest.subject.hasIdentifier()) {
                    val patient = awsPatient.getPatient(newMedicationRequest.subject.identifier)
                    if (patient != null) awsBundleProvider.updateReference(newMedicationRequest.subject, patient.identifierFirstRep,patient)
                }
        }
        if (newMedicationRequest.hasRequester() && bundle != null) {
                val practitionerRole = awsPractitionerRole.get(newMedicationRequest.requester,bundle)
                if (practitionerRole != null) awsBundleProvider.updateReference(newMedicationRequest.requester, practitionerRole.identifierFirstRep, practitionerRole)
        }
        // This v3esquw data should have been processed into propoer resources so remove
        newMedicationRequest.contained = ArrayList()

        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is MedicationRequest
        ) {
            val medicationRequest = awsBundle.entryFirstRep.resource as MedicationRequest
            // Dont update for now - just return aws MedicationRequest
            return updateMedicationRequest(medicationRequest, newMedicationRequest)!!.resource as MedicationRequest
        } else {
            return createMedicationRequest(newMedicationRequest)!!.resource as MedicationRequest
        }
    }

    public fun getMedicationRequest(identifier: Identifier): MedicationRequest? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(MedicationRequest::class.java)
                    .where(
                        MedicationRequest.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as MedicationRequest
    }

    private fun updateMedicationRequest(medicationRequest: MedicationRequest, newMedicationRequest: MedicationRequest): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newMedicationRequest.identifier) {
            var found = false
            for (awsidentifier in medicationRequest.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                medicationRequest.addIdentifier(identifier)
                changed = true
            }
        }

        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(medicationRequest)
        var retry = 3
        while (retry > 0) {
            try {
                newMedicationRequest.id = medicationRequest.idElement.value
                response = awsClient!!.update().resource(newMedicationRequest).withId(medicationRequest.id).execute()
                log.info("AWS MedicationRequest updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(medicationRequest, AuditEvent.AuditEventAction.C)
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

    private fun createMedicationRequest(newMedicationRequest: MedicationRequest): MethodOutcome? {

        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newMedicationRequest)
                    .execute()
                val medicationRequest = response.resource as MedicationRequest
                val auditEvent = awsAuditEvent.createAudit(medicationRequest, AuditEvent.AuditEventAction.C)
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
