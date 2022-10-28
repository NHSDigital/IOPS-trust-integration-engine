package uk.nhs.nhsdigital.pmir.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome

import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.pmir.awsProvider.AWSCommunication
import uk.nhs.nhsdigital.pmir.awsProvider.AWSCommunicationRequest
import javax.servlet.http.HttpServletRequest

@Component
class CommunicationRequestProvider(
                                   var awsCommunicationRequest: AWSCommunicationRequest,
                                   var awsCommunication: AWSCommunication
) : IResourceProvider {
    override fun getResourceType(): Class<CommunicationRequest> {
        return CommunicationRequest::class.java
    }
   
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam communicationRequest: CommunicationRequest): MethodOutcome? {
       val outcome = awsCommunicationRequest.create(communicationRequest)
        if (outcome!=null && outcome.resource is CommunicationRequest) {
            awsCommunication.create(outcome.resource as CommunicationRequest)
        }
        return outcome
    }

}
