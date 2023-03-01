package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class CommunicationProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor)  {



    @Search(type=Communication::class)
    fun search(httpRequest : HttpServletRequest,
               @OptionalParam(name = Communication.SP_RECIPIENT) recipient: ReferenceParam?,
               @OptionalParam(name = Communication.SP_SENDER) sender : ReferenceParam?,
               @OptionalParam(name= Communication.SP_STATUS) status : TokenParam?
               ): Bundle? {
        val list = mutableListOf<Communication>()
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, httpRequest.queryString,"Communication")
        if (resource != null && resource is Bundle) {
            return resource
        }
        return null
    }


}
