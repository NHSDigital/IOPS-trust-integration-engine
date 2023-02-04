package uk.nhs.england.pmir.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.param.UriParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.pmir.awsProvider.AWSQuestionnaire

import javax.servlet.http.HttpServletRequest

@Component
class QuestionnaireProvider(
                                   var awsQuestionnaire: AWSQuestionnaire

) : IResourceProvider {
    override fun getResourceType(): Class<Questionnaire> {
        return Questionnaire::class.java
    }

    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam communicationRequest: Questionnaire): MethodOutcome? {
        return awsQuestionnaire.create(communicationRequest)
    }

    @Search
    fun search(httpRequest : HttpServletRequest,
               @OptionalParam(name = Questionnaire.SP_URL) uriParam: UriParam?
    ): List<Questionnaire>? {
        return awsQuestionnaire.search(uriParam)
    }

}
