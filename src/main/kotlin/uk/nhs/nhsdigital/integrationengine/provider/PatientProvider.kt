package uk.nhs.nhsdigital.integrationengine.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.DateRangeParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.integrationengine.awsProvider.AWSPatient
import uk.nhs.nhsdigital.integrationengine.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class PatientProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
var awsPatient: AWSPatient) : IResourceProvider {
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

        var method = MethodOutcome().setCreated(true)
        method.resource = awsPatient.createUpdateAWSPatient(patient)
        return method
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam patient: Patient): MethodOutcome? {

        return cognitoAuthInterceptor.updatePost(theRequest,patient)

    }

}
