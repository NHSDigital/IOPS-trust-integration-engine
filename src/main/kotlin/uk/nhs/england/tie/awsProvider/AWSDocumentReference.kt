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
class AWSDocumentReference(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                           @Qualifier("R4") val ctx: FhirContext,
                           val fhirServerProperties: FHIRServerProperties,
                           val awsOrganization: AWSOrganization,
                           val awsPractitioner: AWSPractitioner,
                           val awsPatient: AWSPatient,
                           val awsBundleProvider: AWSBundle,
                           val awsEncounter: AWSEncounter,
                           val awsAuditEvent: AWSAuditEvent
) {

    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdateAWSDocumentReference(newDocumentReference: DocumentReference, bundle: Bundle?): DocumentReference? {
        var awsBundle: Bundle? = null
        if (!newDocumentReference.hasIdentifier()) throw UnprocessableEntityException("DocumentReference has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newDocumentReference.identifier) {
                if (identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("DocumentReference has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(DocumentReference::class.java)
                    .where(
                        DocumentReference.IDENTIFIER.exactly()
                            .code(nhsIdentifier.value)
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
        // Custodian
        if (newDocumentReference.hasCustodian() && newDocumentReference.getCustodian().hasIdentifier()) {
            val organisation = awsOrganization.get(newDocumentReference.custodian.identifier)
            if (organisation != null) awsBundleProvider.updateReference(newDocumentReference.custodian, organisation.identifierFirstRep, organisation)
        }
        // Patient
        if (newDocumentReference.hasSubject()) {
            if (newDocumentReference.subject.hasReference() && bundle != null) {
                val patient = awsPatient.get(newDocumentReference.subject, bundle)
                if (patient != null) awsBundleProvider.updateReference(newDocumentReference.subject, patient.identifierFirstRep, patient )
            } else
                if (newDocumentReference.subject.hasIdentifier()) {
                    val patient = awsPatient.get(newDocumentReference.subject.identifier)
                    if (patient != null) awsBundleProvider.updateReference(newDocumentReference.subject, patient.identifierFirstRep, patient )
                }
        }
        if (newDocumentReference.hasContext() && newDocumentReference.context.hasEncounter()) {
          //TODO
        }
        // Author
        for (participant in newDocumentReference.author) {
            if (participant.hasIdentifier()) {
                if (participant.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                    participant.identifier.system.equals(FhirSystems.NHS_GMP_NUMBER)) {
                    val dr = awsPractitioner.get(participant.identifier)
                    if (dr != null) {
                        awsBundleProvider.updateReference(participant, dr.identifierFirstRep, dr)
                    }
                }
                if (participant.identifier.system.equals(FhirSystems.ODS_CODE)) {
                        val organisation = awsOrganization.get(participant.identifier)
                        if (organisation != null) awsBundleProvider.updateReference(participant, organisation.identifierFirstRep, organisation)
                    }
            }
        }

       if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is DocumentReference
        ) {
            val documentReference = awsBundle.entryFirstRep.resource as DocumentReference
            // Dont update for now - just return aws DocumentReference
            return update(documentReference, newDocumentReference)!!.resource as DocumentReference
        } else {
            return create(newDocumentReference)!!.resource as DocumentReference
        }
    }

    public fun get(identifier: Identifier): DocumentReference? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(DocumentReference::class.java)
                    .where(
                        DocumentReference.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as DocumentReference
    }


    private fun update(documentReference: DocumentReference, newDocumentReference: DocumentReference): MethodOutcome? {
        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient!!.update().resource(newDocumentReference).withId(documentReference.id).execute()
                log.info("AWS DocumentReference updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(documentReference, AuditEvent.AuditEventAction.C)
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

    private fun create(newDocumentReference: DocumentReference): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newDocumentReference)
                    .execute()
                val documentReference = response.resource as DocumentReference
                val auditEvent = awsAuditEvent.createAudit(documentReference, AuditEvent.AuditEventAction.C)
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

    fun transform(newDocumentReference: DocumentReference): Resource? {
        if (newDocumentReference.hasCustodian() && newDocumentReference.getCustodian().hasIdentifier()) {
            val organisation = awsOrganization.get(newDocumentReference.custodian.identifier)
            if (organisation != null) awsBundleProvider.updateReference(newDocumentReference.custodian, organisation.identifierFirstRep, organisation)
        }
        // Patient
        if (newDocumentReference.hasSubject()) {
            if (newDocumentReference.subject.hasIdentifier()) {
                val patient = awsPatient.get(newDocumentReference.subject.identifier)
                if (patient != null) awsBundleProvider.updateReference(newDocumentReference.subject, patient.identifierFirstRep, patient )
            }
        }
        if (newDocumentReference.hasContext() && newDocumentReference.context.hasEncounter()) {
            //TODO
        }
        // Author
        for (participant in newDocumentReference.author) {
            if (participant.hasIdentifier()) {
                if (participant.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)||
                    participant.identifier.system.equals(FhirSystems.NHS_GMP_NUMBER)) {
                    val dr = awsPractitioner.get(participant.identifier)
                    if (dr != null) {
                        awsBundleProvider.updateReference(participant, dr.identifierFirstRep, dr)
                    }
                }
                if (participant.identifier.system.equals(FhirSystems.ODS_CODE)) {
                    val organisation = awsOrganization.get(participant.identifier)
                    if (organisation != null) awsBundleProvider.updateReference(participant, organisation.identifierFirstRep, organisation)
                }
            }
        }
        return newDocumentReference
    }
}
