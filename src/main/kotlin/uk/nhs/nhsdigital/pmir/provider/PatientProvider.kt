package uk.nhs.nhsdigital.pmir.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.pmir.awsProvider.AWSPatient
import uk.nhs.nhsdigital.pmir.interceptor.CognitoAuthInterceptor
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
        method.resource = awsPatient.createUpdateAWSPatient(patient, null)
        return method
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam patient: Patient): MethodOutcome? {

        var method = MethodOutcome().setCreated(true)
        method.resource = awsPatient.createUpdateAWSPatient(patient, null)
        return method
    }

}
