package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.DateRangeParam
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

import javax.servlet.http.HttpServletRequest

@Component
class CareTeamPlainProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor)  {



    @Search(type=CareTeam::class)
    fun search(
        httpRequest : HttpServletRequest,
        @OptionalParam(name = CareTeam.SP_DATE) date: DateRangeParam?,
        @OptionalParam(name = CareTeam.SP_PATIENT) patient: ReferenceParam?,
        @OptionalParam(name = CareTeam.SP_STATUS) status: TokenParam?,
        @OptionalParam(name = CareTeam.SP_IDENTIFIER)  identifier :TokenParam?,
        @OptionalParam(name = CareTeam.SP_RES_ID)  resid : StringParam?
    ): Bundle? {
        val careTeams = mutableListOf<CareTeam>()
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, httpRequest.queryString,"CareTeam")
        if (resource != null && resource is Bundle) {
            return resource
        }

        return null
    }
}
