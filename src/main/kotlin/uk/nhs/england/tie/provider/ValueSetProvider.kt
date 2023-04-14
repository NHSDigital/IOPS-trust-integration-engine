package uk.nhs.england.tie.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.ConceptValidationOptions
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.context.support.IValidationSupport.CodeValidationResult
import ca.uhn.fhir.context.support.ValidationSupportContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.param.DateParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import mu.KLogging
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.configuration.MessageProperties
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class ValueSetProvider (@Qualifier("R4") private val fhirContext: FhirContext,
                        private val cognitoAuthInterceptor: CognitoAuthInterceptor,
                        private val messageProperties: MessageProperties
) : IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */
    override fun getResourceType(): Class<ValueSet> {
        return ValueSet::class.java
    }


    companion object : KLogging()


    @Operation(name = "\$validate-code", idempotent = true)
    fun validateCode (
        httpRequest : HttpServletRequest,
        @OperationParam(name = "url") url: String?,
        @OperationParam(name = "context") context: String?,
        @ResourceParam valueSet: ValueSet?,
        @OperationParam(name = "valueSetVersion") valueSetVersion: String?,
        @OperationParam(name = "code") code: String?,
        @OperationParam(name = "system") system: String?,
        @OperationParam(name = "systemVersion") systemVersion: String?,
        @OperationParam(name = "display") display: String?,
        @OperationParam(name = "coding") coding: TokenParam?,
        @OperationParam(name = "codeableConcept") codeableConcept: CodeableConcept?,
        @OperationParam(name = "date") date: DateParam?,
        @OperationParam(name = "abstract") abstract: BooleanType?,
        @OperationParam(name = "displayLanguage") displayLanguage: CodeType?
    ) : OperationOutcome?  {

            val resource: Resource? = cognitoAuthInterceptor.readFromUrl(messageProperties.getValidationFhirServer()+"/FHIR/R4",httpRequest.pathInfo, httpRequest.queryString,"ValueSet")
            if (resource != null && resource is OperationOutcome) {
                return resource
            }
            return null
    }

    @Operation(name = "\$expand", idempotent = true)
    fun expand(
        httpRequest : HttpServletRequest,
        @ResourceParam valueSet: ValueSet?,
        @OperationParam(name = ValueSet.SP_URL) url: TokenParam?,
        @OperationParam(name = "filter") filter: StringParam?): ValueSet? {

        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(messageProperties.getValidationFhirServer()+"/FHIR/R4",httpRequest.pathInfo, httpRequest.queryString,"ValueSet")
        if (resource != null && resource is ValueSet) {
            return resource
        }
        return null
    }
}
