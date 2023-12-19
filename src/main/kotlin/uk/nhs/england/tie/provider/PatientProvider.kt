package uk.nhs.england.tie.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.component.PatientSummary
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class PatientProvider(var awsPatient: AWSPatient, var cognitoAuthInterceptor: CognitoAuthInterceptor,
                      val client: IGenericClient, @Qualifier("R4") val ctxFHIR : FhirContext, val templateEngine: TemplateEngine
) : IResourceProvider {
    override fun getResourceType(): Class<Patient> {
        return Patient::class.java
    }

    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam patient: Patient,
        @IdParam theId: IdType?,
        @ConditionalUrlParam theConditional : String?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {

        val method = MethodOutcome().setCreated(true)
        method.resource = awsPatient.createUpdate(patient, null)
        return method
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam patient: Patient): MethodOutcome? {

        val method = MethodOutcome().setCreated(true)
        method.resource = awsPatient.createUpdate(patient, null)
        return method
    }

    @Operation(name = "summary", idempotent = true)
    fun convertOpenAPI(@IdParam patientId: IdType): Bundle? {
        var patientSummary = PatientSummary(client,ctxFHIR,templateEngine)
        return patientSummary.getCareRecord(patientId.idPart)

    }

}
