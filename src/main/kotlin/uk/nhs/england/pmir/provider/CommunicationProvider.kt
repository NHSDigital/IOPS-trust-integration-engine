package uk.nhs.england.pmir.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.pmir.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class CommunicationProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor) : IResourceProvider {
    override fun getResourceType(): Class<Communication> {
        return Communication::class.java
    }


    @Search
    fun search(httpRequest : HttpServletRequest,
               @OptionalParam(name = Communication.SP_RECIPIENT) recipient: ReferenceParam?,
               @OptionalParam(name = Communication.SP_SENDER) sender : ReferenceParam?,
               @OptionalParam(name= Communication.SP_STATUS) status : TokenParam?
               ): List<Communication> {
        val list = mutableListOf<Communication>()
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, httpRequest.queryString)
        if (resource != null && resource is Bundle) {
            for (entry in resource.entry) {
                if (entry.hasResource() && entry.resource is Communication) list.add(entry.resource as Communication)
            }
        }
        return list
    }


}
