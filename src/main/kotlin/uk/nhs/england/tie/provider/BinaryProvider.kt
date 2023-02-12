package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.Create
import ca.uhn.fhir.rest.annotation.IdParam
import ca.uhn.fhir.rest.annotation.Read
import ca.uhn.fhir.rest.annotation.ResourceParam
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSBinary
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class BinaryProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor, val awsBinary: AWSBinary) : IResourceProvider {
    override fun getResourceType(): Class<Binary> {
        return Binary::class.java
    }

    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): Binary? {
        return awsBinary.get(internalId)
    }
    @Create
    fun create(httpRequest : HttpServletRequest, @ResourceParam binary: Binary): MethodOutcome? {
        val inputStream = httpRequest.inputStream
        val outcome = awsBinary.create(binary)
        return outcome
    }

}
