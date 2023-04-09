package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSActivityDefinition
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

import javax.servlet.http.HttpServletRequest

@Component
class ActivityDefinitionProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                                 var awsActivityDefinition: AWSActivityDefinition) : IResourceProvider {
    override fun getResourceType(): Class<ActivityDefinition> {
        return ActivityDefinition::class.java
    }


    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam carePlan: ActivityDefinition,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        if (!carePlan.hasIdentifier()) throw UnprocessableEntityException("ActivityDefinition identifier is required")
        return awsActivityDefinition.update(carePlan,null,theId)
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam carePlan: ActivityDefinition): MethodOutcome? {
        if (!carePlan.hasIdentifier()) throw UnprocessableEntityException("ActivityDefinition identifier is required")
        return awsActivityDefinition.create(carePlan,null)
    }

    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): ActivityDefinition? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"ActivityDefinition")
        return if (resource is ActivityDefinition) resource else null
    }
    @Delete
    fun create(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsActivityDefinition.delete(theId)
    }

}
