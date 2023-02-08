package uk.nhs.england.pmir.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.DateRangeParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.pmir.awsProvider.AWSPatient
import uk.nhs.england.pmir.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class PatientProvider(var awsPatient: AWSPatient,var cognitoAuthInterceptor: CognitoAuthInterceptor) : IResourceProvider {
    override fun getResourceType(): Class<Patient> {
        return Patient::class.java
    }

    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam patient: Patient,
        @IdParam theId: IdType?,
        @ConditionalUrlParam theConditional : String?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {

        val method = MethodOutcome().setCreated(true)
        method.resource = awsPatient.createUpdate(patient, null)
        return method
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam patient: Patient): MethodOutcome? {

        val method = MethodOutcome().setCreated(true)
        method.resource = awsPatient.createUpdate(patient, null)
        return method
    }

}
