package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.OptionalParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.*
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import jakarta.servlet.http.HttpServletRequest

@Component
class ServiceRequestPlainProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                                  val awsPatient: AWSPatient
)  {




    @Search(type=ServiceRequest::class)
    fun search(
        httpRequest : HttpServletRequest,
        @OptionalParam(name = ServiceRequest.SP_PATIENT) serviceRequest : ReferenceParam?,
        @OptionalParam(name = "patient:identifier") nhsNumber : TokenParam?,
        @OptionalParam(name = ServiceRequest.SP_AUTHORED)  date : DateRangeParam?,
        @OptionalParam(name = ServiceRequest.SP_CATEGORY)  category :TokenParam?,
        @OptionalParam(name = ServiceRequest.SP_IDENTIFIER)  identifier :TokenParam?,
        @OptionalParam(name = ServiceRequest.SP_STATUS)  status: TokenOrListParam?,
        @OptionalParam(name = ServiceRequest.SP_RES_ID)  resid : StringParam?,
        @OptionalParam(name = "_getpages")  pages : StringParam?,
        @OptionalParam(name = "_count")  count : StringParam?
    ): Bundle? {
        val queryString = awsPatient.processQueryString(httpRequest.queryString,nhsNumber)
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, queryString,"ServiceRequest")
        if (resource != null && resource is Bundle) {
            return resource
        }

        return null
    }
}
