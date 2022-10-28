package uk.nhs.nhsdigital.pmir.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.param.UriParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.pmir.awsProvider.AWSQuestionnaire
import uk.nhs.nhsdigital.pmir.interceptor.CognitoAuthInterceptor

import javax.servlet.http.HttpServletRequest

@Component
class QuestionnaireProvider(
                                   var awsQuestionnaire: AWSQuestionnaire,
                                   var cognitoAuthInterceptor: CognitoAuthInterceptor
) : IResourceProvider {
    override fun getResourceType(): Class<Questionnaire> {
        return Questionnaire::class.java
    }
   
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam communicationRequest: Questionnaire): MethodOutcome? {
       val outcome = awsQuestionnaire.create(communicationRequest)
        return outcome
    }

    @Search
    fun search(httpRequest : HttpServletRequest,
               @OptionalParam(name = Questionnaire.SP_URL) uriParam: UriParam?
    ): List<Questionnaire>? {
        return awsQuestionnaire.seach(uriParam)
    }

}
