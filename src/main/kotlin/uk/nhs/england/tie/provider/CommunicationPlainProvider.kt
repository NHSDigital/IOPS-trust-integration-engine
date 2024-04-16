package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSCommunication
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import jakarta.servlet.http.HttpServletRequest

@Component
class CommunicationPlainProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                                 val awsPatient: AWSPatient)  {

    @Search(type=Communication::class)
    fun search(httpRequest : HttpServletRequest,
               @OptionalParam(name = Communication.SP_PATIENT) patient: ReferenceParam?,
               @OptionalParam(name = "patient:identifier") nhsNumber : TokenParam?,
               @OptionalParam(name = Communication.SP_RECIPIENT) recipient: ReferenceParam?,
               @OptionalParam(name = Communication.SP_SENDER) sender : ReferenceParam?,
               @OptionalParam(name= Communication.SP_STATUS) status : TokenParam?
               ): Bundle? {
        val queryString :String? = awsPatient.processQueryString(httpRequest.queryString,nhsNumber)

        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, queryString,"Communication")
       if (resource != null && resource is Bundle) {
            return resource
        }
        return null
    }




}
