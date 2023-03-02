package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSCareTeam
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

import javax.servlet.http.HttpServletRequest

@Component
class CareTeamProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
    var awsCareTeam: AWSCareTeam) : IResourceProvider {
    override fun getResourceType(): Class<CareTeam> {
        return CareTeam::class.java
    }


    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam careTeam: CareTeam,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        if (!careTeam.hasIdentifier()) throw UnprocessableEntityException("CareTeam identifier is required")
        return cognitoAuthInterceptor.updatePost(theRequest,careTeam)
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam careTeam: CareTeam): MethodOutcome? {
        if (!careTeam.hasIdentifier()) throw UnprocessableEntityException("CareTeam identifier is required")
        return awsCareTeam.create(careTeam,null)

    }

    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): CareTeam? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"CareTeam")
        return if (resource is CareTeam) resource else null
    }
    @Delete
    fun create(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsCareTeam.delete(theId)
    }

}
