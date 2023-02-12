package uk.nhs.england.tie.interceptor

import ca.uhn.fhir.interceptor.api.Hook
import ca.uhn.fhir.interceptor.api.Interceptor
import ca.uhn.fhir.interceptor.api.Pointcut
import ca.uhn.fhir.rest.api.server.RequestDetails
import org.hl7.fhir.instance.model.api.IBaseResource
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest


@Interceptor
class ValidationInterceptor {

    private val log = LoggerFactory.getLogger("FHIRAudit")
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
    fun incomingRequest(request: HttpServletRequest, requestDetails: RequestDetails?, resource: IBaseResource?) {
       log.info(request.method)
    }
    /*
    protected fun handleFailure(theOutcome: IRepositoryValidatingRule.RuleEvaluation) {
        if (theOutcome.getOperationOutcome() != null) {
            val firstIssue = OperationOutcomeUtil.getFirstIssueDetails(myFhirContext, theOutcome.getOperationOutcome())
            throw PreconditionFailedException(firstIssue, theOutcome.getOperationOutcome())
        }
        throw PreconditionFailedException(theOutcome.getFailureDescription())
    }
*/
}

