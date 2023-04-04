package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.*

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.awsProvider.AWSServiceRequest
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class ServiceRequestProvider(var awsServiceRequest: AWSServiceRequest,
                             var awsPatient: AWSPatient,
    val cognitoAuthInterceptor: CognitoAuthInterceptor) : IResourceProvider {
    override fun getResourceType(): Class<ServiceRequest> {
        return ServiceRequest::class.java
    }

    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam serviceRequest: ServiceRequest): MethodOutcome? {

        val method = MethodOutcome().setCreated(true)
        method.resource = awsServiceRequest.createUpdate(serviceRequest,null)
        return method
    }

    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam serviceRequest: ServiceRequest,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        val method = MethodOutcome().setCreated(false)
        if (!serviceRequest.hasIdentifier()) throw UnprocessableEntityException("ServiceRequest identifier is required")
        method.resource = awsServiceRequest.createUpdate(serviceRequest,null)
        return method
    }

    @Read(type=ServiceRequest::class)
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): ServiceRequest? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"ServiceRequest")
        return if (resource is ServiceRequest) resource else null
    }

    @Delete
    fun delete(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsServiceRequest.delete(theId)
    }


}
