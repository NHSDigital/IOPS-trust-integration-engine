package uk.nhs.england.tie.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import mu.KLogging
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSObservation
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import jakarta.servlet.http.HttpServletRequest

@Component
class ObservationProvider (@Qualifier("R4") private val fhirContext: FhirContext,
                           private val cognitoAuthInterceptor: CognitoAuthInterceptor,
                           private val awsObservation: AWSObservation
) :IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */

    override fun getResourceType(): Class<Observation> {
        return Observation::class.java
    }

    companion object : KLogging()
    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam observation: Observation,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        if (!observation.hasIdentifier()) throw UnprocessableEntityException("observation identifier is required")
        return awsObservation.update(observation,theId)
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam observation: Observation): MethodOutcome? {
        if (!observation.hasIdentifier()) throw UnprocessableEntityException("observation identifier is required")
        return awsObservation.create(observation)
    }

    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): Observation? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"observation")
        return if (resource is Observation) resource else null
    }

    @Delete
    fun create(theRequest: HttpServletRequest,  @IdParam theId: IdType): MethodOutcome? {
        return awsObservation.delete(theId)
    }
}
