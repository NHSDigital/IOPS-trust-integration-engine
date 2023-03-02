package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome

import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSEpisodeOfCare
import javax.servlet.http.HttpServletRequest

@Component
class EpisodeOfCareProvider(var awsEpisodeOfCare: AWSEpisodeOfCare) : IResourceProvider {
    override fun getResourceType(): Class<EpisodeOfCare> {
        return EpisodeOfCare::class.java
    }

    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam encounter: EpisodeOfCare): MethodOutcome? {

        val method = MethodOutcome().setCreated(true)
        method.resource = awsEpisodeOfCare.createUpdate(encounter)
        return method
    }

}
