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
import java.io.OutputStream
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

    @Operation(name = "summary", idempotent = true, manualResponse = true)
    fun convertOpenAPI(
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        @IdParam patientId: IdType,
        @OperationParam(name= "_format") format: StringType?) {
        var patientSummary = PatientSummary(client,ctxFHIR,templateEngine)

        var bundle = patientSummary.getCareRecord(patientId.idPart)
        if (format !== null && (format.value.contains("pdf") || format.value.contains("text") )) {
            val xmlResult = ctxFHIR.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle)
            val html = patientSummary.convertHTML(xmlResult, "XML/DocumentToHTML.xslt")
            if (html !== null) {
                if (format.value.contains("text")) {
                    servletResponse.setContentType("text/html")
                    servletResponse.setCharacterEncoding("UTF-8")
                    servletResponse.writer.write(html)
                    servletResponse.writer.flush()
                    return
                } else if (format.value.contains("pdf")) {
                    servletResponse.setContentType("application/pdf")
                    servletResponse.setCharacterEncoding("UTF-8")
                    var pdfOutputStream = patientSummary.convertPDF(html)
                    if (pdfOutputStream !== null) {
                        val os: OutputStream = servletResponse.getOutputStream()
                        val byteArray = pdfOutputStream.toByteArray()

                        try {
                            os.write(byteArray, 0, byteArray.size)
                        } catch (excp: Exception) {
                            //handle error
                        } finally {
                            os.close()
                        }
                    // has been flushed    servletResponse.writer.flush()
                        return
                    }
                }
            }
        }
        servletResponse.setContentType("application/json")
        servletResponse.setCharacterEncoding("UTF-8")
        servletResponse.writer.write(ctxFHIR.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle))
        servletResponse.writer.flush()
        return
    }

}
