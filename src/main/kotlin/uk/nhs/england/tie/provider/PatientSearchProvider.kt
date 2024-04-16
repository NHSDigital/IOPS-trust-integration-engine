package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.DateRangeParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import jakarta.servlet.http.HttpServletRequest
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

@Component
class PatientSearchProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor) {

    @Read(type=Patient::class)
    fun read( httpRequest : HttpServletRequest,@IdParam internalId: IdType): Patient? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo,  null, null)
        return if (resource is Patient) resource else null
    }
    @Search(type = Patient::class)
    fun search(
        httpRequest : HttpServletRequest,
        @OptionalParam(name = Patient.SP_ADDRESS_POSTALCODE) addressPostcode : StringParam?,
        @OptionalParam(name= Patient.SP_BIRTHDATE) birthDate : DateRangeParam?,
        @OptionalParam(name= Patient.SP_EMAIL) email : StringParam?,
        @OptionalParam(name = Patient.SP_FAMILY) familyName : StringParam?,
        @OptionalParam(name= Patient.SP_GENDER) gender : StringParam?,
        @OptionalParam(name= Patient.SP_GIVEN) givenName : StringParam?,
        @OptionalParam(name = Patient.SP_IDENTIFIER) identifier : TokenParam?,
        @OptionalParam(name= Patient.SP_NAME) name : StringParam?,
        @OptionalParam(name= Patient.SP_TELECOM) phone : StringParam?,
        @OptionalParam(name = "_getpages")  pages : StringParam?,
        @OptionalParam(name = "_count")  count : StringParam?
    ): Bundle? {
        val patients = mutableListOf<Patient>()
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, httpRequest.queryString,"Patient")
        if (resource != null && resource is Bundle) {
            return resource
        }
        return null
    }

}
