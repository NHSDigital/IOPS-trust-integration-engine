package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.OptionalParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.*
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import jakarta.servlet.http.HttpServletRequest
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

@Component
class TaskPlainProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor, val awsPatient: AWSPatient) {


    @Search(type=Task::class)
    fun search(
        httpRequest : HttpServletRequest,
        @OptionalParam(name = Task.SP_PATIENT) task : ReferenceParam?,
        @OptionalParam(name = "patient:identifier") nhsNumber : TokenParam?,
        @OptionalParam(name = Task.SP_AUTHORED_ON)  date : DateRangeParam?,
        @OptionalParam(name = Task.SP_CODE)  code :TokenParam?,
        @OptionalParam(name = Task.SP_IDENTIFIER)  identifier :TokenParam?,
        @OptionalParam(name = Task.SP_STATUS)  status: TokenOrListParam?,
        @OptionalParam(name = Task.SP_RES_ID)  resid : StringParam?,
        @OptionalParam(name = Task.SP_FOCUS) focus : ReferenceParam?,
        @OptionalParam(name = "_getpages")  pages : StringParam?,
        @OptionalParam(name = "_count")  count : StringParam?
    ): Bundle?{
        val queryString = awsPatient.processQueryString(httpRequest.queryString,nhsNumber)
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, queryString,"Task")
        if (resource != null && resource is Bundle) {
            return resource
        }
        return null
    }
}
