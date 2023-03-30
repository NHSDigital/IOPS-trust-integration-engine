package uk.nhs.england.tie.provider

import ca.uhn.fhir.context.FhirContext

import ca.uhn.fhir.rest.annotation.Transaction
import ca.uhn.fhir.rest.annotation.TransactionParam
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import mu.KLogging
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent
import org.hl7.fhir.r4.model.Bundle.BundleType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.*
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor


@Component
class TransactionProvider(
    @Qualifier("R4") private val fhirContext: FhirContext,
    val cognitoAuthInterceptor: CognitoAuthInterceptor,
    val fhirServerProperties: FHIRServerProperties,
    val awsServiceRequest: AWSServiceRequest,
    val awsPatient: AWSPatient,
    val awsPractitioner: AWSPractitioner,
    val awsOrganization: AWSOrganization,
    val awsTask : AWSTask,
    val awsBinary: AWSBinary,
    val awsCommunication: AWSCommunication,
    val awsDocumentReference: AWSDocumentReference,
    val awsAppointment: AWSAppointment,
    val awsSpecimen: AWSSpecimen,
    val awsSchedule: AWSSchedule,
    val awsConsent: AWSConsent,
    val awsPractitionerRole: AWSPractitionerRole,
    val awsObservation: AWSObservation,
    val awsDiagnosticReport: AWSDiagnosticReport,
    val awsEncounter: AWSEncounter,
    val awsGoal: AWSGoal,
    val awsCarePlan: AWSCarePlan,
    val awsEpisodeOfCare: AWSEpisodeOfCare,
    val awsCondition: AWSCondition,
    val awsQuestionnaireResponse: AWSQuestionnaireResponse,
    val awsMedicationRequest: AWSMedicationRequest,
    val awsBundle: AWSBundle) {

    companion object : KLogging()

    @Transaction
    fun transaction(@TransactionParam bundle:Bundle,
    ): Bundle? {
        // only process document metadata
        if (!bundle.type.equals(BundleType.TRANSACTION)) throw UnprocessableEntityException("Payload is not a FHIR Transaction Bundle");
        val transaction = processTransaction(bundle)

       val response = awsBundle.transaction(transaction)
        return response
    }



    fun processTransaction(bundle: Bundle) : Bundle
    {
        val transaction = Bundle();
        transaction.id = bundle.id
        transaction.type = Bundle.BundleType.TRANSACTION

        if (bundle.hasEntry()) {
            for (entry in bundle.entry) {
                if (entry.hasResource()) {
                    when (entry.resource.resourceType.name) {
                        "MessageHeader", "Binary" -> {
                            // Do nothing
                            // Binary is added via the DocumentReference link below
                        }
                        "DocumentReference" -> {
                            val document = entry.resource as DocumentReference
                            if (document.hasContent()) {
                                for(content in document.content) {
                                    if (content.hasAttachment()) {
                                        val attachment = content.attachment
                                        if (attachment.hasUrl()) {
                                            val entry = awsBundle.findResource(bundle, "Binary", attachment.url)
                                            if (entry != null && entry is Binary) {
                                                val outcome = awsBinary.create(entry)
                                                if (outcome != null && outcome.resource != null && outcome.resource is Binary)  content.attachment.url = fhirServerProperties.server.baseUrl + "/FHIR/R4/Binary/" +(outcome.resource as Binary).id
                                            }
                                        }
                                    }
                                }
                            }
                            transaction.addEntry(lookupIdentifiers(entry))
                        }
                        else -> {
                            transaction.addEntry(lookupIdentifiers(entry))
                        }
                    }

                }
            }
        }
      //  logger.info(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(transaction))
        return transaction

    }
    fun getIdentifier(identifier: Identifier) : String {
        if (identifier.hasSystem()) return identifier.system + "|" + identifier.value
        return identifier.value
    }
    fun lookupIdentifiers(entry : BundleEntryComponent) : BundleEntryComponent {
        if (entry.hasResource()) {
            when (entry.resource.resourceType.name) {
                "Appointment" -> {
                    entry.resource = awsAppointment.transform(entry.resource as Appointment)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Appointment).hasIdentifier()) {
                        val result = awsAppointment.get((entry.resource as Appointment).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Appointment).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Appointment/" + getId(result.idElement)
                        }
                    }
                }
                "CarePlan" -> {
                    entry.resource = awsCarePlan.transform(entry.resource as CarePlan, null)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as CarePlan).hasIdentifier()) {
                        val result = awsCarePlan.get((entry.resource as CarePlan).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as CarePlan).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "CarePlan/" + getId(result.idElement)
                        }
                    }
                }
                "Condition" -> {
                    entry.resource = awsCondition.transform(entry.resource as Condition)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Condition).hasIdentifier()) {
                        val result = awsCondition.get((entry.resource as Condition).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Condition).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Condition/" + getId(result.idElement)
                        }
                    }
                }
                "Communication" -> {
                    entry.resource = awsCommunication.transform(entry.resource as Communication)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Communication).hasIdentifier()) {
                        val result = awsCommunication.get((entry.resource as Communication).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Communication).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Communication/" + getId(result.idElement)
                        }
                    }
                }
                "Consent" -> {
                    entry.resource = awsConsent.transform(entry.resource as Consent)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Consent).hasIdentifier()) {
                        val result = awsConsent.get((entry.resource as Consent).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Consent).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Consent/" + getId(result.idElement)
                        }
                    }
                }
                "DiagnosticReport" -> {
                    entry.resource = awsDiagnosticReport.transform(entry.resource as DiagnosticReport)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as DiagnosticReport).hasIdentifier()) {
                        val result = awsDiagnosticReport.get((entry.resource as DiagnosticReport).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as DiagnosticReport).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "DiagnosticReport/" + getId(result.idElement)
                        }
                    }
                }
                "DocumentReference" -> {
                    entry.resource = awsDocumentReference.transform(entry.resource as DocumentReference)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as DocumentReference).hasIdentifier()) {
                        val result = awsDocumentReference.get((entry.resource as DocumentReference).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as DocumentReference).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "DocumentReference/" + getId(result.idElement)
                        }
                    }
                }
                "Encounter" -> {
                    entry.resource = awsEncounter.transform(entry.resource as Encounter)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Encounter).hasIdentifier()) {
                        val result = awsEncounter.get((entry.resource as Encounter).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Encounter).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Encounter/" + getId(result.idElement)
                        }
                    }
                }
                "EpisodeOfCare" -> {
                    entry.resource = awsEpisodeOfCare.transform(entry.resource as EpisodeOfCare)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as EpisodeOfCare).hasIdentifier()) {
                        val result = awsEpisodeOfCare.get((entry.resource as EpisodeOfCare).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as EpisodeOfCare).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "EpisodeOfCare/" + getId(result.idElement)
                        }
                    }
                }
                "Goal" -> {
                    entry.resource = awsGoal.transform(entry.resource as Goal, null)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Goal).hasIdentifier()) {
                        val result = awsGoal.get((entry.resource as Goal).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Goal).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Goal/" + getId(result.idElement)
                        }
                    }
                }
                "MedicationRequest" -> {
                    entry.resource = awsMedicationRequest.transform(entry.resource as MedicationRequest)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as MedicationRequest).hasIdentifier()) {
                        val result = awsMedicationRequest.get((entry.resource as MedicationRequest).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as MedicationRequest).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "MedicationRequest/" + getId(result.idElement)
                        }
                    }
                }
                "Observation" -> {
                    entry.resource = awsObservation.transform(entry.resource as Observation)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Observation).hasIdentifier()) {
                        val result = awsObservation.get((entry.resource as Observation).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Observation).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Observation/" + getId(result.idElement)
                        }
                    }
                }
                "Organization" -> {
                    entry.resource = awsOrganization.transform(entry.resource as Organization)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Organization).hasIdentifier()) {
                        val result = awsOrganization.get((entry.resource as Organization).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Organization).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Organization/" + getId(result.idElement)
                        }
                    }
                }

                "Patient" -> {
                    entry.resource = awsPatient.transform(entry.resource as Patient)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Patient).hasIdentifier()) {
                        val result = awsPatient.get((entry.resource as Patient).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Patient).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Patient/" + getId(result.idElement)
                        }
                    }
                }
                "Practitioner" -> {
                    entry.resource = awsPractitioner.transform(entry.resource as Practitioner)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Practitioner).hasIdentifier()) {
                        val result = awsPractitioner.get((entry.resource as Practitioner).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Practitioner).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Practitioner/" + getId(result.idElement)
                        }
                    }
                }
                "PractitionerRole" -> {
                    entry.resource = awsPractitionerRole.transform(entry.resource as PractitionerRole)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as PractitionerRole).hasIdentifier()) {
                        val result = awsPractitionerRole.get((entry.resource as PractitionerRole).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as PractitionerRole).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "PractitionerRole/" + getId(result.idElement)
                        }
                    }
                }

                "QuestionnaireResponse" -> {
                    entry.resource = awsQuestionnaireResponse.transform(entry.resource as QuestionnaireResponse)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as QuestionnaireResponse).hasIdentifier()) {
                        val result = awsQuestionnaireResponse.get((entry.resource as QuestionnaireResponse).identifier)
                        if (result != null) {
                            (entry.resource as QuestionnaireResponse).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "QuestionnaireResponse/" + getId(result.idElement)
                        }
                    }
                }
                "ServiceRequest" -> {
                    entry.resource = awsServiceRequest.transform(entry.resource as ServiceRequest)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as ServiceRequest).hasIdentifier()) {
                        val result = awsServiceRequest.get((entry.resource as ServiceRequest).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as ServiceRequest).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "ServiceRequest/" + getId(result.idElement)
                        }
                    }
                }
                "Schedule" -> {
                    entry.resource = awsSchedule.transform(entry.resource as Schedule)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Schedule).hasIdentifier()) {
                        val result = awsSchedule.get((entry.resource as Schedule).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Schedule).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Schedule/" + getId(result.idElement)
                        }
                    }
                }
                "Specimen" -> {
                    entry.resource = awsSpecimen.transform(entry.resource as Specimen)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Specimen).hasIdentifier()) {
                        val result = awsSpecimen.get((entry.resource as Specimen).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Specimen).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Specimen/" + getId(result.idElement)
                        }
                    }
                }
                "Task" -> {
                    entry.resource = awsTask.transform(entry.resource as Task)
                    if (entry.request.method.equals(Bundle.HTTPVerb.POST) && (entry.resource as Task).hasIdentifier()) {
                        val result = awsTask.get((entry.resource as Task).identifierFirstRep)
                        if (result != null) {
                            (entry.resource as Task).id = getId(result.idElement)
                            entry.request.method = Bundle.HTTPVerb.PUT
                            entry.request.url =
                                "Task/" + getId(result.idElement)
                        }
                    }
                }




            }

        }
        return entry
    }

    private fun getId(id: IdType): String {
        return id.idPart
    }
}




