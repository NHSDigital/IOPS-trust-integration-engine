package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSCondition
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

import jakarta.servlet.http.HttpServletRequest

@Component
class ConditionProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                        var awsCondition: AWSCondition) : IResourceProvider {
    override fun getResourceType(): Class<Condition> {
        return Condition::class.java
    }


    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam condition: Condition,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        if (!condition.hasIdentifier()) throw UnprocessableEntityException("condition identifier is required")
        return awsCondition.update(condition,theId)
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam condition: Condition): MethodOutcome? {
        if (!condition.hasIdentifier()) throw UnprocessableEntityException("condition identifier is required")
        return awsCondition.create(condition)
    }

    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): Condition? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"condition")
        return if (resource is Condition) resource else null
    }
    @Delete
    fun create(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsCondition.delete(theId)
    }

}
