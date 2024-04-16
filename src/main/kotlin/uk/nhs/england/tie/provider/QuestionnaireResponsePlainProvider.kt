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


import jakarta.servlet.http.HttpServletRequest

@Component
class QuestionnaireResponsePlainProvider(

    var cognitoAuthInterceptor: CognitoAuthInterceptor,
    val awsPatient: AWSPatient

) {

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



}
