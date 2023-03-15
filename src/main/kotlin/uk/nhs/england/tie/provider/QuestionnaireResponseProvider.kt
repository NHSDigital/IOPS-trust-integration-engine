package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome

import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.param.UriParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSQuestionnaire
import uk.nhs.england.tie.awsProvider.AWSQuestionnaireResponse
import java.util.*


import javax.servlet.http.HttpServletRequest

@Component
class QuestionnaireResponseProvider(
                                   var awsQuestionnaireResponse: AWSQuestionnaireResponse,
    var awsQuestionnaire: AWSQuestionnaire

) : IResourceProvider {
    override fun getResourceType(): Class<QuestionnaireResponse> {
        return QuestionnaireResponse::class.java
    }

    @Create
    fun create(
        theRequest: HttpServletRequest,
        @ResourceParam questionnaireResponse: QuestionnaireResponse
    ): MethodOutcome? {
        return awsQuestionnaireResponse.createUpdate(questionnaireResponse)
    }

    @Operation(name = "\$extract", idempotent = true)
    fun expand(@ResourceParam questionnaireResponse: QuestionnaireResponse
    ): Bundle {
        var bundle: Bundle = Bundle();
        bundle.type = Bundle.BundleType.TRANSACTION;
        if (!questionnaireResponse.hasQuestionnaire()) throw UnprocessableEntityException("Questionnaire must be supplied");
        val questionnaire = awsQuestionnaire.search(UriParam().setValue(questionnaireResponse?.questionnaire))
        if (questionnaire == null || questionnaire.size==0) throw UnprocessableEntityException("Questionnaire not found");
        for(item in questionnaireResponse.item) {
           var questionItem = getItem(questionnaire[0], item.linkId)
            var generateObservation = false;
            if (questionItem.hasExtension()) {
                for (extension in questionItem.extension) {
                    if (extension.url.equals("http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-observationExtract")
                        && extension.value is BooleanType) {
                        generateObservation = (extension.value as BooleanType).value
                    }
                }
            }
            if (generateObservation && questionItem.hasCode() && item.answerFirstRep != null) {
                var observation = Observation()
                observation.status = Observation.ObservationStatus.FINAL;
                if (questionnaireResponse.hasIdentifier()) {
                    var identifier = Identifier()
                    identifier.system = questionnaireResponse.identifier.system
                    identifier.value = questionnaireResponse.identifier.value + item.linkId
                    observation.addIdentifier(identifier)
                }
                if (questionnaireResponse.hasAuthor()) {
                    observation.addPerformer(questionnaireResponse.author)
                }
                observation.code = CodeableConcept()
                    observation.code.coding = questionItem.code
                for (answer in item.answer) {
                    if (answer.hasValueQuantity()) {
                        observation.setValue(answer.valueQuantity)
                    }
                    if (answer.hasValueCoding()) {
                        observation.setValue(answer.valueCoding)
                    }
                    if (answer.hasValueDecimalType()) {
                        observation.setValue(Quantity().setValue(answer.valueDecimalType.value))
                    }
                    if (answer.hasValueIntegerType()) {
                        observation.setValue(answer.valueIntegerType)
                    }
                }
                observation.setSubject(questionnaireResponse.subject)
                if (questionnaireResponse.hasAuthored()) {
                    observation.setEffective(DateTimeType().setValue(questionnaireResponse.authored ))
                }
                var entry = BundleEntryComponent()
                var uuid = UUID.randomUUID();
                entry.fullUrl = "urn:uuid:" + uuid.toString()
                entry.resource = observation
                entry.request.url = "Observation"
                entry.request.method = Bundle.HTTPVerb.POST
                bundle.entry.add(entry)
            }
        }
        return bundle;
    }

    private fun getItem(questionnaire: Questionnaire, linkId: String): Questionnaire.QuestionnaireItemComponent {
        for (item in questionnaire.item) {
            if (linkId.equals(item.linkId)) return item;
        }
        throw UnprocessableEntityException("linkId not found " + linkId)
    }


}
