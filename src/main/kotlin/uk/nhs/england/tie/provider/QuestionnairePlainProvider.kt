package uk.nhs.england.tie.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.DateParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import mu.KLogging
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.awsProvider.AWSQuestionnaire
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import jakarta.servlet.http.HttpServletRequest

@Component
class QuestionnairePlainProvider (@Qualifier("R4") private val fhirContext: FhirContext,
                                  var awsPatient: AWSPatient,
                                  private val cognitoAuthInterceptor: CognitoAuthInterceptor
)  {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */


    companion object : KLogging()

    @Search(type=Questionnaire::class)
    fun search(
        httpRequest: HttpServletRequest,
        @OptionalParam(name = Questionnaire.SP_CODE) code: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_URL) url: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_CONTEXT) context: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_DATE) date: DateParam?,
        @OptionalParam(name = Questionnaire.SP_IDENTIFIER) identifier: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_PUBLISHER) publisher: StringParam?,
        @OptionalParam(name = Questionnaire.SP_STATUS) status: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_TITLE) title: StringParam?,
        @OptionalParam(name = Questionnaire.SP_VERSION) version: TokenParam?,
        @OptionalParam(name = Questionnaire.SP_DEFINITION) definition: TokenParam?,
        @OptionalParam(name = "_getpages")  pages : StringParam?,
        @OptionalParam(name = "_count")  count : StringParam?
    ): Bundle? {
        val queryString = awsPatient.processQueryString(httpRequest.queryString,null)
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, queryString,"Questionnaire")
        if (resource != null && resource is Bundle) {
            return resource
        }
        return null
    }


}
