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
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class PatientProvider(var awsPatient: AWSPatient, var cognitoAuthInterceptor: CognitoAuthInterceptor,
                      val client: IGenericClient, @Qualifier("R4") val ctxFHIR : FhirContext, val templateEngine: TemplateEngine
) : IResourceProvider {
    override fun getResourceType(): Class<Patient> {
        return Patient::class.java
    }

    var df: DateFormat = SimpleDateFormat("HHmm_dd_MM_yyyy")

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

    @Operation(name = "summary", idempotent = true, manualResponse = true)
    fun convertOpenAPI(
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        @IdParam patientId: IdType,
        @OperationParam(name="_format") format : String?) {
        var patientSummary = PatientSummary(client,ctxFHIR,templateEngine)
        val summary = patientSummary.getCareRecord(patientId.idPart)
        if (format !== null && (format.equals("application/pdf") || format.equals("text/html"))) {
            val date = Date()
            var xmlResult = ctxFHIR.newXmlParser().encodeResourceToString(summary)
            var html = patientSummary.convertToHtml(xmlResult,"XML/DocumentToHTML.xslt");
            if (html !== null && format.equals("text/html")) {
                servletResponse.setContentType("text/html")
                servletResponse.setCharacterEncoding("UTF-8")
                servletResponse.writer.write(html)
                servletResponse.writer.flush()
            } else if (html !== null && format.equals("application/pdf")) {
                servletResponse.setContentType("text/html")
                servletResponse.setCharacterEncoding("UTF-8")
                servletResponse.writer.write(html)
                servletResponse.writer.flush()
            }
        }
        else {
            servletResponse.setContentType("application/json")
            servletResponse.setCharacterEncoding("UTF-8")
            servletResponse.writer.write(ctxFHIR.newJsonParser().setPrettyPrint(true).encodeResourceToString(summary))
            servletResponse.writer.flush()
        }
        return
    }

}
