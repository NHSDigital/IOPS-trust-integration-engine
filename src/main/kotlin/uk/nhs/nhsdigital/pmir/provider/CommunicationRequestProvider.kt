package uk.nhs.nhsdigital.pmir.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.pmir.awsProvider.AWSCommunicationRequest
import uk.nhs.nhsdigital.pmir.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class CommunicationRequestProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                                   var awsCommunicationRequest: AWSCommunicationRequest) : IResourceProvider {
    override fun getResourceType(): Class<CommunicationRequest> {
        return CommunicationRequest::class.java
    }
   
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam communicationRequest: CommunicationRequest): MethodOutcome? {
        return awsCommunicationRequest.createCommunicationRequest(communicationRequest)
    }

}
