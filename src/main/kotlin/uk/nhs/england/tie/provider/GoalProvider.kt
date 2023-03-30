package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSGoal
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

import javax.servlet.http.HttpServletRequest

@Component
class GoalProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                   var awsGoal: AWSGoal) : IResourceProvider {
    override fun getResourceType(): Class<Goal> {
        return Goal::class.java
    }


    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam goal: Goal,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        if (!goal.hasIdentifier()) throw UnprocessableEntityException("goal identifier is required")
        return awsGoal.update(goal,null,theId)
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam goal: Goal): MethodOutcome? {
        if (!goal.hasIdentifier()) throw UnprocessableEntityException("goal identifier is required")
        return awsGoal.create(goal,null)
    }

    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): Goal? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"goal")
        return if (resource is Goal) resource else null
    }
    @Delete
    fun create(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsGoal.delete(theId)
    }

}
