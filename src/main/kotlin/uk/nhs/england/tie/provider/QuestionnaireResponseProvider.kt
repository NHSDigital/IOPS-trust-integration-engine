package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.UriParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.awsProvider.AWSQuestionnaire
import uk.nhs.england.tie.awsProvider.AWSQuestionnaireResponse
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import java.util.*


import javax.servlet.http.HttpServletRequest

@Component
class QuestionnaireResponseProvider(
    var awsQuestionnaireResponse: AWSQuestionnaireResponse,
    var awsQuestionnaire: AWSQuestionnaire,
    var cognitoAuthInterceptor: CognitoAuthInterceptor,
    val awsPatient: AWSPatient

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

    @Delete
    fun create(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsQuestionnaireResponse.delete(theId)
    }

    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam questionnaireResponse: QuestionnaireResponse,
        @IdParam theId: IdType?,
        @ConditionalUrlParam theConditional : String?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {

        return awsQuestionnaireResponse.update(questionnaireResponse, theId)

    }

    @Read(type=QuestionnaireResponse::class)
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): QuestionnaireResponse? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,null)
        return if (resource is QuestionnaireResponse) resource else null
    }


    @Operation(name = "\$extract", idempotent = true, canonicalUrl = "http://hl7.org/fhir/uv/sdc/OperationDefinition/QuestionnaireResponse-extract")
    fun expand(@ResourceParam questionnaireResponse: QuestionnaireResponse
    ): Bundle {
        var bundle: Bundle = Bundle();
        bundle.type = Bundle.BundleType.TRANSACTION;
        if (!questionnaireResponse.hasQuestionnaire()) throw UnprocessableEntityException("Questionnaire must be supplied");
        val questionnaire = awsQuestionnaire.search(UriParam().setValue(questionnaireResponse?.questionnaire))
        if (questionnaire == null || questionnaire.size==0) {
            var result = awsQuestionnaire.read(IdType().setValue(questionnaireResponse.questionnaire))
            if (result !== null && result.resource !== null) {
                processItem(bundle, result.resource as Questionnaire, questionnaireResponse, questionnaireResponse.item)
            } else {
                throw UnprocessableEntityException("Questionnaire not found")
            }
        } else {
            processItem(bundle, questionnaire[0], questionnaireResponse, questionnaireResponse.item)
        }
        return bundle;
    }

    private fun processItem(bundle: Bundle, questionnaire: Questionnaire, questionnaireResponse: QuestionnaireResponse, items: List<QuestionnaireResponse.QuestionnaireResponseItemComponent>) {
        for(item in items) {
            var questionItem = getItem(questionnaire, item.linkId)
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

                for (answer in item.answer) {
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
                    observation.setSubject(questionnaireResponse.subject)
                    if (questionnaireResponse.hasAuthored()) {
                        observation.setEffective(DateTimeType().setValue(questionnaireResponse.authored ))
                        observation.setIssued(questionnaireResponse.authored )
                    }
                    if (answer.hasValueQuantity()) {
                        observation.setValue(answer.valueQuantity)
                    }
                    if (answer.hasValueCoding()) {
                        observation.setValue(CodeableConcept().addCoding(answer.valueCoding))
                    }
                    if (answer.hasValueDecimalType()) {
                        observation.setValue(Quantity().setValue(answer.valueDecimalType.value))
                    }
                    if (answer.hasValueIntegerType()) {
                        observation.setValue(answer.valueIntegerType)
                    }
                    if (answer.hasValueDateType()) {
                        observation.setEffective(DateTimeType().setValue(answer.valueDateType.value))
                    }
                    if (answer.hasValueDateTimeType()) {
                        observation.setEffective(answer.valueDateTimeType)
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
            if (item.hasItem()) {
                processItem(bundle, questionnaire, questionnaireResponse, item.item)
            }
        }
    }

    private fun getItem(questionnaire: Questionnaire, linkId: String): Questionnaire.QuestionnaireItemComponent {
        var result = getSubItems(questionnaire.item, linkId)
        if (result != null) return result
        throw UnprocessableEntityException("linkId not found " + linkId)
    }

    private fun getSubItems(items : List<Questionnaire.QuestionnaireItemComponent>, linkId: String): Questionnaire.QuestionnaireItemComponent? {
        for (item in items) {
            if (linkId.equals(item.linkId)) return item;
            if (item.hasItem()) {
                var result = getSubItems(item.item, linkId)
                if (result != null) return result
            }
        }
        return null
    }


}
