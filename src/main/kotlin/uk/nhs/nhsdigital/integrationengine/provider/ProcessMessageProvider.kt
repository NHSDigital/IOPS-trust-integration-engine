package uk.nhs.nhsdigital.integrationengine.provider

import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.ResourceParam
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.integrationengine.awsProvider.*
import uk.nhs.nhsdigital.integrationengine.util.FhirSystems

@Component
class ProcessMessageProvider(val awsMedicationRequest: AWSMedicationRequest,
                             val awsMedicationDispense: AWSMedicationDispense,
                             val awsServiceRequest: AWSServiceRequest,
                             val awsTask : AWSTask,
                             val awsBundle: AWSBundle) {

    @Operation(name = "\$process-message", idempotent = true)
    fun expand(@ResourceParam bundle:Bundle,
             ): OperationOutcome? {
        val filterMessageHeaders = awsBundle.filterResources(bundle,"MessageHeader")
        var focusResources = ArrayList<Resource>()
        var operationOutcome = OperationOutcome();
        var focusType : String? = null
        if (filterMessageHeaders.size > 0) {
            val messageHeader = filterMessageHeaders[0] as MessageHeader
            if (messageHeader.hasEventCoding()) {
                when (messageHeader.eventCoding.code) {
                    "prescription-order" -> {
                        focusType = "MedicationRequest"
                    }
                    "dispense-notification", "dispense-notification-update" -> {
                        focusType = "MedicationDispense"

                    }
                    "servicerequest-request" -> {
                        focusType = "ServiceRequest"
                    }
                }
                var medicationRequest : MedicationRequest? = null
                var prescriptionOrder = Task().setCode(CodeableConcept().addCoding(Coding()
                    .setSystem(FhirSystems.SNOMED_CT)
                    .setCode("16076005")
                    .setDisplay("Prescription")
                ))
                if (focusType != null) {
                    focusResources.addAll(awsBundle.filterResources(bundle,focusType))

                    if (focusResources.size>0) {
                        for (workerResource in focusResources) {
                            when (focusType) {
                                "MedicationRequest" -> {
                                    medicationRequest = awsMedicationRequest.createUpdateAWSMedicationRequest(workerResource as MedicationRequest,bundle)
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
                                    val medicationDispense = awsMedicationDispense.createUpdateAWSMedicationDispense(workerResource as MedicationDispense,bundle)
                                    if (medicationDispense != null) {
                                        operationOutcome.issue.add(
                                            OperationOutcome.OperationOutcomeIssueComponent()
                                                .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                                .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                                .addLocation(medicationDispense.id))
                                    }
                                }
                                "ServiceRequest" -> {
                                    val serviceRequest = awsServiceRequest.createUpdateAWSServiceRequest(workerResource as ServiceRequest,bundle)
                                    if (serviceRequest != null) {
                                        operationOutcome.issue.add(
                                            OperationOutcome.OperationOutcomeIssueComponent()
                                                .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                                .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                                .addLocation(serviceRequest.id))
                                    }
                                }
                            }
                        }
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
                            awsTask.createUpdateAWSTask(prescriptionOrder)
                        }

                    }
                }
            }

        }

        return operationOutcome
    }



}
