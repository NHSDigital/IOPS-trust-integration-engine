package uk.nhs.nhsdigital.integrationengine.awsProvider

import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.DomainResource
import org.hl7.fhir.r4.model.Resource
import org.springframework.stereotype.Component

@Component
class AWSBundle {

    fun filterResources(bundle: Bundle, resourceType : String): List<Resource> {
        val resource = ArrayList<Resource>()

        for (entry in bundle.entry) {
            if (entry.hasResource() && entry.resource.resourceType.name.equals(resourceType)) {
                resource.add(entry.resource)
            }
        }
        return resource
    }
    fun findResource(bundle: Bundle, resourceType : String, reference : String): Resource? {
        val resource = ArrayList<Resource>()

        for (entry in bundle.entry) {
            if (entry.hasResource()) {
                if (entry.resource.resourceType.name.equals(resourceType)
                    && entry.fullUrl.equals(reference))
                {
                    return entry.resource
                }
                if (entry.resource is DomainResource) {
                    val domainResource : DomainResource = entry.resource as DomainResource
                    if (domainResource.hasContained()) {
                        for( contained in domainResource.contained) {
                            if (contained.id.equals(reference)) return contained
                        }
                    }
                }
            }
        }
        return null
    }
}
