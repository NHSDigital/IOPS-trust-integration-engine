package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.DateRangeParam
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

import jakarta.servlet.http.HttpServletRequest

@Component
class AuditEventPlainProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                              var awsPatient: AWSPatient)  {



    @Search(type=AuditEvent::class)
    fun search(
        httpRequest : HttpServletRequest,
        @OptionalParam(name = AuditEvent.SP_DATE) date: DateRangeParam?,
        @OptionalParam(name = AuditEvent.SP_PATIENT) patient: ReferenceParam?,
        @OptionalParam(name = AuditEvent.SP_AGENT) agent: ReferenceParam?,
        @OptionalParam(name = "patient:identifier") nhsNumber : TokenParam?,
        @OptionalParam(name = AuditEvent.SP_RES_ID)  resid : StringParam?,
        @OptionalParam(name = AuditEvent.SP_ADDRESS)  address : StringParam?
    ): Bundle? {

        val queryString :String? = awsPatient.processQueryString(httpRequest.queryString,nhsNumber)

        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, queryString,"AuditEvent")
        if (resource != null && resource is Bundle) {
            return resource
        }

        return null
    }
}
