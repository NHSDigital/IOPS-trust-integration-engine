package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.ResourceParam
import ca.uhn.fhir.rest.annotation.Transaction
import ca.uhn.fhir.rest.annotation.TransactionParam
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.*
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.util.FhirSystems

@Component
class TransactionProvider(
    val fhirServerProperties: FHIRServerProperties,
    val awsServiceRequest: AWSServiceRequest,
    val awsPatient: AWSPatient,
    val awsTask : AWSTask,
    val awsBinary: AWSBinary,
    val awsDocumentReference: AWSDocumentReference,
    val awsAppointment: AWSAppointment,
    val awsSpecimen: AWSSpecimen,
    val awsConsent: AWSConsent,
    val awsPractitionerRole: AWSPractitionerRole,
    val awsBundle: AWSBundle) {


    @Transaction
    fun transaction(@TransactionParam bundle:Bundle,
    ): Bundle {
        // only process document metadata
        val list = processTransaction(bundle,"DocumentReference")
        val bundle = Bundle()
        bundle.type = Bundle.BundleType.TRANSACTIONRESPONSE
        for (resource in list) {
            bundle.entry.add(Bundle.BundleEntryComponent()
                //.setResource(resource)
                .setResponse(Bundle.BundleEntryResponseComponent()
                    .setStatus("200 OK")
                    .setLocation(resource.id)
                )
            )
        }
        return bundle
    }



    fun processTransaction(bundle: Bundle, focusType : String) : List<Resource>
    {
        val returnBundle = ArrayList<Resource>()
        if (bundle.hasEntry()) {
            for (entry in bundle.entry) {
                if (entry.hasResource()) {
                    val workerResource = entry.resource
                    if (workerResource is DocumentReference) {
                        val document = workerResource as DocumentReference
                        if (document.hasContent()) {
                            for(content in document.content) {
                                if (content.hasAttachment()) {
                                    val attachment = content.attachment
                                    if (attachment.hasUrl()) {
                                        val entry = awsBundle.findResource(bundle, "Binary", attachment.url)
                                        if (entry != null && entry is Binary) {
                                            val outcome = awsBinary.create(entry)
                                            if (outcome != null && outcome.resource != null && outcome.resource is Binary)  content.attachment.url = fhirServerProperties.server.baseUrl + "/Binary/" +(outcome.resource as Binary).id
                                        }
                                    }
                                }
                            }
                        }
                        val documentReference = awsDocumentReference.createUpdateAWSDocumentReference(
                            workerResource as DocumentReference, bundle)
                        if (documentReference != null) {
                            returnBundle.add(documentReference)
                        }
                    }
                    if (workerResource is ServiceRequest) {
                        val serviceRequest = awsServiceRequest.createUpdate(workerResource as ServiceRequest,bundle)
                        if (serviceRequest != null) {
                            returnBundle.add(serviceRequest)
                        }
                    }
                    if (workerResource is Task) {
                        val task = awsTask.createUpdate(workerResource as Task,bundle)
                        if (task != null) {
                            returnBundle.add(task)
                        }
                    }
                    if (workerResource is Appointment) {
                        val appointment = awsAppointment.createUpdate(workerResource as Appointment)
                        if (appointment != null) {
                            returnBundle.add(appointment)
                        }
                    }
                    if (workerResource is Specimen) {
                        val specimen = awsSpecimen.createUpdate(workerResource as Specimen,bundle)
                        if (specimen != null) {
                            returnBundle.add(specimen)
                        }
                    }
                    if (workerResource is Consent) {
                        val consent = awsConsent.createUpdate(workerResource as Consent,bundle)
                        if (consent != null) {
                            returnBundle.add(consent)
                        }
                    }
                    if (workerResource is PractitionerRole) {
                        val practitionerRole = awsPractitionerRole.createUpdate(workerResource as PractitionerRole,bundle)
                        if (practitionerRole != null ) {
                            returnBundle.add(practitionerRole as Resource)
                        }
                    }
                }
            }
        }
        return returnBundle
    }


}
