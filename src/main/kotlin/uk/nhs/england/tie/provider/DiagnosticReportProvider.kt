package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.*

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.awsProvider.AWSDiagnosticReport
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class DiagnosticReportProvider(var awsDiagnosticReport: AWSDiagnosticReport,
                               var awsPatient: AWSPatient,
                               val cognitoAuthInterceptor: CognitoAuthInterceptor) : IResourceProvider {
    override fun getResourceType(): Class<DiagnosticReport> {
        return DiagnosticReport::class.java
    }

    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam diagnosticReport: DiagnosticReport): MethodOutcome? {

        val method = MethodOutcome().setCreated(true)
        method.resource = awsDiagnosticReport.createUpdate(diagnosticReport,null, OperationOutcome())
        return method
    }

    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam diagnosticReport: DiagnosticReport,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        val method = MethodOutcome().setCreated(false)
        if (!diagnosticReport.hasIdentifier()) throw UnprocessableEntityException("DiagnosticReport identifier is required")
        method.resource = awsDiagnosticReport.createUpdate(diagnosticReport,null, OperationOutcome())
        return method
    }

    @Read(type=DiagnosticReport::class)
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): DiagnosticReport? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"DiagnosticReport")
        return if (resource is DiagnosticReport) resource else null
    }

    @Delete
    fun delete(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsDiagnosticReport.delete(theId)
    }


}
