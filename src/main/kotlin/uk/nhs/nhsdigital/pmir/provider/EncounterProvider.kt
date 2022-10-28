package uk.nhs.nhsdigital.pmir.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome

import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.pmir.awsProvider.AWSEncounter
import javax.servlet.http.HttpServletRequest

@Component
class EncounterProvider(var awsEncounter: AWSEncounter) : IResourceProvider {
    override fun getResourceType(): Class<Encounter> {
        return Encounter::class.java
    }

    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam encounter: Encounter): MethodOutcome? {

        val method = MethodOutcome().setCreated(true)
        method.resource = awsEncounter.createUpdate(encounter)
        return method
    }

}
