package uk.nhs.england.tie.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import mu.KLogging
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSObservation
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

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


    @Delete
    fun create(theRequest: HttpServletRequest,  @IdParam theId: IdType): MethodOutcome? {
        return awsObservation.delete(theId)
    }
}
