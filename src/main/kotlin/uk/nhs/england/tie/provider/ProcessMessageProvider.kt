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
class ProcessMessageProvider(
    val fhirServerProperties: FHIRServerProperties,
    val awsMedicationRequest: AWSMedicationRequest,
    val awsMedicationDispense: AWSMedicationDispense,
    val awsServiceRequest: AWSServiceRequest,
    val awsObservation: AWSObservation,
    val awsDiagnosticReport: AWSDiagnosticReport,
    val awsPatient: AWSPatient,
    val awsRelatedPerson: AWSRelatedPerson,
    val awsTask : AWSTask,
    val awsBinary: AWSBinary,
    val awsDocumentReference: AWSDocumentReference,
    val awsBundle: AWSBundle) {

    @Operation(name = "\$process-message", idempotent = true)
    fun expand(@ResourceParam bundle:Bundle,
             ): OperationOutcome? {
        val filterMessageHeaders = awsBundle.filterResources(bundle,"MessageHeader")

        val operationOutcome = OperationOutcome()
        var focusType : String? = null
        if (filterMessageHeaders.size > 0) {
            val messageHeader = filterMessageHeaders[0] as MessageHeader
            if (messageHeader.hasEventCoding()) {
                when (messageHeader.eventCoding.code) {
                    "prescription-order" -> {
                        focusType = "MedicationRequest"
                    }
                    "pds-change-of-address-1" ,
                    "pds-birth-notification-1",
                    "pds-death-notification-1",
                    "pds-change-of-gp-1" -> {
                        focusType = "Patient"
                    }
                    "dispense-notification", "dispense-notification-update" -> {
                        focusType = "MedicationDispense"

                    }
                    "unsolicited-observations"-> {
                        focusType = "DiagnosticReport"
                    }
                    "servicerequest-request" -> {
                        focusType = "ServiceRequest"
                    }
                    "document" -> {
                        focusType = "DocumentReference"
                    }
                }
                var medicationRequest : MedicationRequest? = null
                val prescriptionOrder = Task().setCode(CodeableConcept().addCoding(Coding()
                    .setSystem(FhirSystems.SNOMED_CT)
                    .setCode("16076005")
                    .setDisplay("Prescription")
                ))
                if (focusType != null) {
                    medicationRequest = processFocusResource(bundle,focusType,prescriptionOrder,operationOutcome)
                    if (focusType.equals("Patient")) {
                        processFocusResource(bundle,"RelatedPerson",prescriptionOrder,operationOutcome)
                        processFocusResource(bundle,"Observation",prescriptionOrder,operationOutcome)
                    }
                }
                // Task processing for requests, n/a for event resource/messages
                when (messageHeader.eventCoding.code) {
                    "prescription-order" -> {

                        if (medicationRequest != null) {
                            prescriptionOrder.setFor(medicationRequest.subject)
                            val groupIdentifier = medicationRequest.groupIdentifier

                            prescriptionOrder.addIdentifier(Identifier()
                                .setSystem(groupIdentifier.system)
                                .setValue(groupIdentifier.value)
                            )
                            prescriptionOrder.setGroupIdentifier(Identifier()
                                .setSystem(groupIdentifier.system)
                                .setValue(groupIdentifier.value))

                            val id = medicationRequest.groupIdentifier.getExtensionByUrl("https://fhir.nhs.uk/StructureDefinition/Extension-DM-PrescriptionId")
                            if (id != null) {
                                if (id.value is Identifier) {
                                    prescriptionOrder.addIdentifier(Identifier()
                                        .setSystem((id.value as Identifier).system)
                                        .setValue((id.value as Identifier).value)
                                    )
                                }
                            }
                            prescriptionOrder.setStatus(Task.TaskStatus.REQUESTED)
                            prescriptionOrder.setIntent(Task.TaskIntent.ORDER)
                            prescriptionOrder.setAuthoredOn(medicationRequest.authoredOn)
                            awsTask.createUpdate(prescriptionOrder)
                        }

                    }
                }
            }

        }

        return operationOutcome
    }




    fun processFocusResource(bundle: Bundle, focusType : String, prescriptionOrder : Task, operationOutcome: OperationOutcome)
    : MedicationRequest?{
        val focusResources = ArrayList<Resource>()
        var medicationRequest: MedicationRequest? = null
        focusResources.addAll(awsBundle.filterResources(bundle,focusType))

        if (focusResources.size>0) {
            for (workerResource in focusResources) {
                when (focusType) {
                    "MedicationRequest" -> {
                        medicationRequest = awsMedicationRequest.createUpdate(workerResource as MedicationRequest,bundle)
                        if (medicationRequest != null) {
                            operationOutcome.issue.add(
                                OperationOutcome.OperationOutcomeIssueComponent()
                                    .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                    .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                    .addLocation(medicationRequest.id))
                            prescriptionOrder.addInput(
                                Task.ParameterComponent()
                                    .setType(CodeableConcept().addCoding(Coding().setCode("MedicationRequest")))
                                    .setValue(Reference().setReference(medicationRequest.idElement.value))
                            )
                            awsTask
                        }
                    }
                    "MedicationDispense" -> {
                        val medicationDispense = awsMedicationDispense.createUpdate(workerResource as MedicationDispense,bundle)
                        if (medicationDispense != null) {
                            operationOutcome.issue.add(
                                OperationOutcome.OperationOutcomeIssueComponent()
                                    .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                    .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                    .addLocation(medicationDispense.id))
                        }
                    }
                    "ServiceRequest" -> {
                        val serviceRequest = awsServiceRequest.createUpdate(workerResource as ServiceRequest,bundle)
                        if (serviceRequest != null) {
                            operationOutcome.issue.add(
                                OperationOutcome.OperationOutcomeIssueComponent()
                                    .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                    .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                    .addLocation(serviceRequest.id))
                        }
                    }
                    "Patient" -> {
                        val patient = awsPatient.createUpdate(workerResource as Patient,bundle)
                        if (patient != null) {
                            operationOutcome.issue.add(
                                OperationOutcome.OperationOutcomeIssueComponent()
                                    .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                    .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                    .addLocation(patient.id))
                        }
                    }
                    "RelatedPerson" -> {
                        val person = awsRelatedPerson.createUpdate(workerResource as RelatedPerson,bundle)
                        if (person != null) {
                            operationOutcome.issue.add(
                                OperationOutcome.OperationOutcomeIssueComponent()
                                    .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                    .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                    .addLocation(person.id))
                        }
                    }
                    "Observation" -> {
                        val observation = awsObservation.createUpdate(workerResource as Observation,bundle)
                        if (observation != null) {
                            operationOutcome.issue.add(
                                OperationOutcome.OperationOutcomeIssueComponent()
                                    .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                    .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                    .addLocation(observation.id))
                        }
                    }
                    "DiagnosticReport" -> {
                        val observation = awsDiagnosticReport.createUpdate(workerResource as DiagnosticReport,bundle, operationOutcome)
                        if (observation != null) {
                            operationOutcome.issue.add(
                                OperationOutcome.OperationOutcomeIssueComponent()
                                    .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                    .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                    .addLocation(observation.id))
                        }
                    }
                    "DocumentReference" -> {
                        val document = workerResource as DocumentReference
                        if (document.hasContent()) {
                            for(content in document.content) {
                                if (content.hasAttachment()) {
                                    val attachment = content.attachment
                                    if (attachment.hasUrl()) {
                                        val entry = awsBundle.findResource(bundle, "Binary", attachment.url)
                                        if (entry != null && entry is Binary) {
                                            val outcome = awsBinary.create(entry)
                                            if (outcome != null && outcome.resource != null && outcome.resource is Binary) {
                                                content.attachment.url = fhirServerProperties.server.baseUrl + "/Binary/" +(outcome.resource as Binary).id
                                                operationOutcome.issue.add(OperationOutcome.OperationOutcomeIssueComponent()
                                                    .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                                    .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                                    .addLocation("Binary/" +(outcome.resource as Binary).id))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val documentReference = awsDocumentReference.createUpdateAWSDocumentReference(
                            workerResource as DocumentReference, bundle)
                        if (documentReference != null) {
                            operationOutcome.issue.add(
                                OperationOutcome.OperationOutcomeIssueComponent()
                                    .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                    .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                    .addLocation(documentReference.id))
                        }
                    }
                }
            }
        }
        return medicationRequest
    }



}
