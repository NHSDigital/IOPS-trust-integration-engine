package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.DateRangeParam
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenOrListParam
import ca.uhn.fhir.rest.param.TokenParam
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest


@Component
class ObservationSearchProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor, var awsPatient: AWSPatient)  {

    @Read(type=Observation::class)
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): Observation? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null, null)
        return if (resource is Observation) resource else null
    }
    @Search(type=Observation::class)
    fun search(
        httpRequest : HttpServletRequest,
        @OptionalParam(name = Observation.SP_PATIENT) patient : ReferenceParam?,
        @OptionalParam(name = "patient:identifier") nhsNumber : TokenParam?,
        @OptionalParam(name = Observation.SP_DATE)  date : DateRangeParam?,
        @OptionalParam(name = Observation.SP_IDENTIFIER)  identifier :TokenParam?,
        @OptionalParam(name = Observation.SP_CODE) code :TokenOrListParam?,
        @OptionalParam(name = Observation.SP_CATEGORY)  category: TokenParam?,
        @OptionalParam(name = Observation.SP_RES_ID)  resid : StringParam?,
        @OptionalParam(name = Observation.SP_STATUS, )  status: TokenOrListParam?,
        @OptionalParam(name = "_getpages")  pages : StringParam?,
        @OptionalParam(name = "_sort")  sort : StringParam?,
        @OptionalParam(name = "_count")  count : StringParam?
    ): Bundle? {
        val queryString = awsPatient.processQueryString(httpRequest.queryString,nhsNumber)
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, queryString, "Observation")
        if (resource != null && resource is Bundle) {
            return resource
        }

        return null
    }
}
