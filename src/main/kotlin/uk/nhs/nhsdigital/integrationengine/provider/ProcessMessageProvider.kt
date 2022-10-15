package uk.nhs.nhsdigital.integrationengine.provider

import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.ResourceParam
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.integrationengine.awsProvider.AWSBundle
import uk.nhs.nhsdigital.integrationengine.awsProvider.AWSMedicationDispense
import uk.nhs.nhsdigital.integrationengine.awsProvider.AWSMedicationRequest
import uk.nhs.nhsdigital.integrationengine.awsProvider.AWSPatient

@Component
class ProcessMessageProvider(val awsMedicationRequest: AWSMedicationRequest,
                             val awsMedicationDispense: AWSMedicationDispense,
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
                    "dispense-notification", "dispense-notification-update" -> {
                        focusType = "ServiceRequest"
                    }
                }
                if (focusType != null) {
                    focusResources.addAll(awsBundle.filterResources(bundle,focusType))
                    if (focusResources.size>0) {
                        for (workerResource in focusResources) {
                            when (focusType) {
                                "MedicationRequest" -> {
                                    val medicationRequest = awsMedicationRequest.createUpdateAWSMedicationRequest(workerResource as MedicationRequest,bundle)
                                    if (medicationRequest != null) {
                                        operationOutcome.issue.add(
                                        OperationOutcome.OperationOutcomeIssueComponent()
                                            .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                            .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                            .addLocation(medicationRequest.id))
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
                            }
                        }
                    }
                }
            }

        }

        return operationOutcome
    }



}
