package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSCommunication
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class CommunicationProvider( var cognitoAuthInterceptor: CognitoAuthInterceptor,
                            val awsCommunication: AWSCommunication) : IResourceProvider {

    private val log = LoggerFactory.getLogger("FHIRAudit")
    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam communication: Communication,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        if (!communication.hasIdentifier()) throw UnprocessableEntityException("Communication identifier is required")
        return awsCommunication.update(communication)
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam communication: Communication): MethodOutcome? {
        if (!communication.hasIdentifier()) throw UnprocessableEntityException("Communication identifier is required")
        return awsCommunication.create(communication)

    }


    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): Communication? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"Communication")
        return if (resource is Communication) resource else null
    }
    @Delete
    fun delete(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsCommunication.delete(theId)
    }

    override fun getResourceType(): Class<Communication> {
        return Communication::class.java
    }


}
