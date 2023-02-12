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
import java.util.*

@Component
class AWSServiceRequest(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                        @Qualifier("R4") val ctx: FhirContext,
                        val fhirServerProperties: FHIRServerProperties,
                        val awsOrganization: AWSOrganization,
                        val awsPractitionerRole: AWSPractitionerRole,
                        val awsPractitioner: AWSPractitioner,
                        val awsPatient: AWSPatient,
                        val awsEncounter: AWSEncounter,
                        val awsDocumentReference: AWSDocumentReference,
                        val awsBundleProvider : AWSBundle,
                        val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdate(newServiceRequest: ServiceRequest, bundle: Bundle?): ServiceRequest? {
        var awsBundle: Bundle? = null
        if (!newServiceRequest.hasIdentifier()) throw UnprocessableEntityException("ServiceRequest has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newServiceRequest.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("ServiceRequest has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(ServiceRequest::class.java)
                    .where(
                        ServiceRequest.IDENTIFIER.exactly()
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

        if (newServiceRequest.hasSubject()) {
            if (newServiceRequest.subject.hasReference() && bundle != null) {
                val patient = awsPatient.get(newServiceRequest.subject, bundle)
                if (patient != null) awsBundleProvider.updateReference(newServiceRequest.subject, patient.identifierFirstRep, patient )
            } else
                if (newServiceRequest.subject.hasIdentifier()) {
                    val patient = awsPatient.get(newServiceRequest.subject.identifier)
                    if (patient != null) awsBundleProvider.updateReference(newServiceRequest.subject, patient.identifierFirstRep, patient )
                }
        }
        if (newServiceRequest.hasRequester() && bundle != null) {
            if (newServiceRequest.requester.resource != null)
                if (newServiceRequest.requester.resource is PractitionerRole) {
                    val practitionerRole = awsPractitionerRole.get(newServiceRequest.requester, bundle)
                    if (practitionerRole != null) awsBundleProvider.updateReference(newServiceRequest.requester, practitionerRole.identifierFirstRep,practitionerRole )
                } else if ((newServiceRequest.requester.resource is Practitioner)) {
                    val practitioner = awsPractitioner.get(newServiceRequest.requester, bundle)
                    if (practitioner != null) awsBundleProvider.updateReference(newServiceRequest.requester, practitioner.identifierFirstRep,practitioner )
                }
        }
        if (newServiceRequest.hasPerformer() && bundle != null) {
            for (performer in newServiceRequest.performer) {
                if (performer.resource != null)
                    if (performer.resource is PractitionerRole) {
                        val practitionerRole = awsPractitionerRole.get(performer, bundle)
                        if (practitionerRole != null) awsBundleProvider.updateReference(performer, practitionerRole.identifierFirstRep,practitionerRole )
                    } else if ((performer.resource is Practitioner)) {
                        val practitioner = awsPractitioner.get(performer, bundle)
                        if (practitioner != null) awsBundleProvider.updateReference(performer, practitioner.identifierFirstRep,practitioner )
                    }
                    else if ((performer.resource is Organization)) {
                        val organization = awsOrganization.get(performer, bundle)
                        if (organization != null) awsBundleProvider.updateReference(performer, organization.identifierFirstRep,organization )
                    }
            }
        }
        if (newServiceRequest.hasEncounter() && newServiceRequest.encounter.resource != null) {
            val encounter = newServiceRequest.encounter.resource as Encounter
            val awsEncounter = awsEncounter.createUpdate(encounter)
            if (awsEncounter != null) awsBundleProvider.updateReference(newServiceRequest.encounter, awsEncounter.identifierFirstRep ,awsEncounter)
        }
        if (newServiceRequest.hasSupportingInfo()) {
            for (reference in newServiceRequest.supportingInfo) {
                if (reference.resource != null) {
                    if (reference.resource is DocumentReference && (reference.resource as DocumentReference).hasIdentifier()) {
                        val documentReference = reference.resource as DocumentReference
                        // TODO should this be createEd
                        val awsDocumentReference = awsDocumentReference.createUpdateAWSDocumentReference(documentReference,bundle)
                        if (awsDocumentReference != null) awsBundleProvider.updateReference(reference, awsDocumentReference.identifierFirstRep ,awsDocumentReference)
                    }
                }
            }
        }
        // This v3esquw data should have been processed into propoer resources so remove
        newServiceRequest.contained = ArrayList()

        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is ServiceRequest
        ) {
            val oldServiceRequest = awsBundle.entryFirstRep.resource as ServiceRequest
            // Dont update for now - just return aws ServiceRequest
            return update(oldServiceRequest, newServiceRequest)!!.resource as ServiceRequest
        } else {
            return create(newServiceRequest)!!.resource as ServiceRequest
        }
    }

    public fun search(identifier: Identifier): ServiceRequest? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(ServiceRequest::class.java)
                    .where(
                        ServiceRequest.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as ServiceRequest
    }

    private fun update(oldServiceRequest: ServiceRequest, newServiceRequest: ServiceRequest): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newServiceRequest.identifier) {
            var found = false
            for (awsidentifier in oldServiceRequest.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                oldServiceRequest.addIdentifier(identifier)
                changed = true
            }
        }

        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(oldServiceRequest)
        var retry = 3
        while (retry > 0) {
            try {
                newServiceRequest.id = oldServiceRequest.idElement.value
                response = awsClient!!.update().resource(newServiceRequest).withId(oldServiceRequest.id).execute()
                log.info("AWS ServiceRequest updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(newServiceRequest, AuditEvent.AuditEventAction.C)
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

    private fun create(newServiceRequest: ServiceRequest): MethodOutcome? {

        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newServiceRequest)
                    .execute()
                val oldServiceRequest = response.resource as ServiceRequest
                val auditEvent = awsAuditEvent.createAudit(oldServiceRequest, AuditEvent.AuditEventAction.C)
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
