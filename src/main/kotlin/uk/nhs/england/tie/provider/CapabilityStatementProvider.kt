package uk.nhs.england.tie.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.RequiredParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.CapabilityStatement
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class CapabilityStatementProvider( private val npmPackages: List<NpmPackage>)  : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<CapabilityStatement> {
        return CapabilityStatement::class.java
    }



    @Search
    fun search(@RequiredParam(name = CapabilityStatement.SP_URL) url: TokenParam): List<CapabilityStatement> {
        val list = mutableListOf<CapabilityStatement>()
        var decodeUri = java.net.URLDecoder.decode(url.value, StandardCharsets.UTF_8.name());

        return list
    }
}
