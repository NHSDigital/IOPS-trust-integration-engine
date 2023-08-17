package uk.nhs.england.tie.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import mu.KLogging
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSObservation
import uk.nhs.england.tie.awsProvider.AWSQuestionnaire
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class QuestionnaireProvider (@Qualifier("R4") private val fhirContext: FhirContext,
                             private val cognitoAuthInterceptor: CognitoAuthInterceptor,
                             private val awsQuestionnaire: AWSQuestionnaire,
                                private val awsObservation: AWSObservation
) :IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */

    override fun getResourceType(): Class<Questionnaire> {
        return Questionnaire::class.java
    }

    companion object : KLogging()



    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): Questionnaire? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"Questionnaire")
        return if (resource is Questionnaire) resource else null
    }

    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam questionnaire: Questionnaire,
        @IdParam theId: IdType,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        return awsQuestionnaire.update(questionnaire, theId)
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam questionnaire: Questionnaire): MethodOutcome? {
        return awsQuestionnaire.create(questionnaire)
    }

    @Delete
    fun create(theRequest: HttpServletRequest,  @IdParam theId: IdType): MethodOutcome? {
        return awsQuestionnaire.delete(theId)
    }

    @Operation(name = "\$populate", idempotent = true, canonicalUrl = "http://hl7.org/fhir/uv/sdc/OperationDefinition/Questionnaire-populate")
    fun expand(
        @OperationParam(name="identifier") identifier: Identifier?,
        @OperationParam(name="canonical") uri: UriType?,
        @OperationParam(name="questionnaire") suppliedQuestionnaire: Questionnaire?,
        @OperationParam(name="questionnaireRef") questionnaireRef: Reference?,
        @OperationParam(name="subject") subject: Reference?
    ): Parameters {
        var questionnaire = suppliedQuestionnaire
        var questionnaireResponse = QuestionnaireResponse()
        if (questionnaireRef !== null && questionnaireRef.hasReference()) {
            questionnaireResponse.questionnaire = questionnaireRef.reference
            var method = awsQuestionnaire.read(IdType().setValue(questionnaireRef.reference))
            if (method !== null && method.resource !== null && method.resource is Questionnaire) {
                questionnaire = method.resource as Questionnaire
            }
        }
        if (subject != null) {
            questionnaireResponse.subject = subject
        }
        if (questionnaire !== null) {
            if (questionnaire.hasItem()) {
                for (item in questionnaire.item) {

                    var qrItem = processItems(subject,  item)
                    if (qrItem !== null) questionnaireResponse.item.add(qrItem)
                }

            }
        }
        var parameters = Parameters()
        parameters.addParameter().setName("response").setResource(questionnaireResponse)
        return parameters
    }
    fun processItems(subject: Reference?,  item: Questionnaire.QuestionnaireItemComponent) : QuestionnaireResponse.QuestionnaireResponseItemComponent? {
        var qrItem :QuestionnaireResponse.QuestionnaireResponseItemComponent? = null

        var extraction = item.getExtensionByUrl("http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-observationExtract")
        if (extraction !== null && extraction.hasValue() && (extraction.value is BooleanType) && (extraction.value as BooleanType).value) {
            var answers = getAnswers(subject, item)
            if (answers.size>0) {
                qrItem = QuestionnaireResponse.QuestionnaireResponseItemComponent()
                qrItem.linkId = item.linkId
                if (item.hasText()) qrItem.text = item.text
                qrItem.answer = answers
            }
        }
        if (item.hasItem()) {
            for (subitem in item.item) {
                var qrSubItem = processItems(subject, subitem)
                if (qrSubItem !== null) {
                    if (qrItem === null) qrItem = QuestionnaireResponse.QuestionnaireResponseItemComponent()
                    qrItem.item.add(qrSubItem)
                    qrItem.linkId = item.linkId
                    if (item.hasText()) qrItem.text = item.text
                }
            }
        }
        return qrItem
    }

    private fun getAnswers(subject : Reference?, item: Questionnaire.QuestionnaireItemComponent) : List<QuestionnaireResponseItemAnswerComponent> {
        var answers = ArrayList<QuestionnaireResponseItemAnswerComponent>()
        if (item.hasCode() && subject !== null && subject.hasReference()) {
            var observations = awsObservation.search(subject, item.code)
            if (observations !== null && observations.size > 0) {
                var doneFirstEntry = false;
                for (observation in observations) {
                    // Only one answer if item does not repeat
                    if (!doneFirstEntry || (item.hasRepeats() && item.repeats)) {

                        if (observation.hasValueQuantity()) {
                            answers.add(
                                QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent()
                                    .setValue(observation.valueQuantity)
                            )
                        }
                        if (observation.hasValueCodeableConcept()) {
                            var exists = false
                            for (answer in answers) {
                                if (answer.hasValueCoding() && answer.valueCoding.code.equals(observation.valueCodeableConcept.codingFirstRep.code)) exists = true
                            }
                            if (!exists) {
                                answers.add(
                                    QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent()
                                        .setValue(observation.valueCodeableConcept.codingFirstRep)
                                )
                            }
                        }
                        doneFirstEntry = true;
                    }
                }
            }
        }
        return answers
    }
}
