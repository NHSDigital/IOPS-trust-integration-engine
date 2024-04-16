package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.param.DateRangeParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSDocumentReference
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import jakarta.servlet.http.HttpServletRequest

@Component
class DocumentReferenceProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
val awsDocumentReference: AWSDocumentReference) : IResourceProvider {
    override fun getResourceType(): Class<DocumentReference> {
        return DocumentReference::class.java
    }

    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam documentReference: DocumentReference): MethodOutcome? {

        var method = MethodOutcome().setCreated(true)
        method.resource = awsDocumentReference.createUpdateAWSDocumentReference(documentReference, null)
        return method
    }
    @Delete
    fun create(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsDocumentReference.delete(theId)
    }
}
