package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSMedicationRequest
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

import javax.servlet.http.HttpServletRequest

@Component
class MedicationRequestProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                                var awsMedicationRequest: AWSMedicationRequest) : IResourceProvider {
    override fun getResourceType(): Class<MedicationRequest> {
        return MedicationRequest::class.java
    }


    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam medicationRequest: MedicationRequest,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        if (!medicationRequest.hasIdentifier()) throw UnprocessableEntityException("medicationRequest identifier is required")
        return awsMedicationRequest.update(medicationRequest,theId)
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam medicationRequest: MedicationRequest): MethodOutcome? {
        if (!medicationRequest.hasIdentifier()) throw UnprocessableEntityException("medicationRequest identifier is required")
        return awsMedicationRequest.create(medicationRequest)
    }

    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): MedicationRequest? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"medicationRequest")
        return if (resource is MedicationRequest) resource else null
    }
    @Delete
    fun create(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsMedicationRequest.delete(theId)
    }

}
