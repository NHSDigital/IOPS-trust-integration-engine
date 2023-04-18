package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSPlanDefinition
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

import javax.servlet.http.HttpServletRequest

@Component
class PlanDefinitionProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                             var awsPlanDefinition: AWSPlanDefinition) : IResourceProvider {
    override fun getResourceType(): Class<PlanDefinition> {
        return PlanDefinition::class.java
    }


    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam carePlan: PlanDefinition,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        if (!carePlan.hasIdentifier()) throw UnprocessableEntityException("PlanDefinition identifier is required")
        return awsPlanDefinition.update(carePlan,null,theId)
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam carePlan: PlanDefinition): MethodOutcome? {
        if (!carePlan.hasIdentifier()) throw UnprocessableEntityException("PlanDefinition identifier is required")
        return awsPlanDefinition.create(carePlan,null)
    }

    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): PlanDefinition? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"PlanDefinition")
        return if (resource is PlanDefinition) resource else null
    }
    @Delete
    fun create(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsPlanDefinition.delete(theId)
    }

}
