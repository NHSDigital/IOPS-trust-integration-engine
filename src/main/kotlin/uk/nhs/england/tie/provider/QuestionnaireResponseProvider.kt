package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam

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
    @Search(type=QuestionnaireResponse::class)
    fun search(httpRequest : HttpServletRequest,
               @OptionalParam(name = QuestionnaireResponse.SP_PATIENT) patient: ReferenceParam?,
               @OptionalParam(name = QuestionnaireResponse.SP_SUBJECT) subject: ReferenceParam?,
               @OptionalParam(name = "patient:identifier") nhsNumber : TokenParam?,
        //.  @OptionalParam(name = QuestionnaireResponse.SP_QUESTIONNAIRE) questionnaire : ReferenceParam?,
               @OptionalParam(name= QuestionnaireResponse.SP_STATUS) status : TokenParam?,
               @OptionalParam(name = "_getpages")  pages : StringParam?,
               @OptionalParam(name = "_count")  count : StringParam?,
               @OptionalParam(name = "_include")  include : StringParam?,
               @OptionalParam(name = "_revinclude") revinclude : StringParam?
    ): Bundle? {
        val queryString = awsPatient.processQueryString(httpRequest.queryString,nhsNumber)
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, queryString,"QuestionnaireResponse")
        if (resource != null && resource is Bundle) {
            return resource
        }
        return null
    }

    @Operation(name = "\$extract", idempotent = true, canonicalUrl = "http://hl7.org/fhir/uv/sdc/OperationDefinition/QuestionnaireResponse-extract")
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
