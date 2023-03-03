package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSCarePlan
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

import javax.servlet.http.HttpServletRequest

@Component
class CarePlanProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                       var awsCarePlan: AWSCarePlan) : IResourceProvider {
    override fun getResourceType(): Class<CarePlan> {
        return CarePlan::class.java
    }


    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam carePlan: CarePlan,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        if (!carePlan.hasIdentifier()) throw UnprocessableEntityException("CarePlan identifier is required")
        return awsCarePlan.update(carePlan,null,theId)
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam carePlan: CarePlan): MethodOutcome? {
        if (!carePlan.hasIdentifier()) throw UnprocessableEntityException("CarePlan identifier is required")
        return awsCarePlan.create(carePlan,null)
    }

    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): CarePlan? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"CarePlan")
        return if (resource is CarePlan) resource else null
    }
    @Delete
    fun create(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsCarePlan.delete(theId)
    }

}
