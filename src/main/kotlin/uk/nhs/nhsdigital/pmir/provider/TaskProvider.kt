package uk.nhs.nhsdigital.pmir.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.pmir.awsProvider.AWSTask
import uk.nhs.nhsdigital.pmir.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class TaskProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                   var awsTask: AWSTask) : IResourceProvider {
    override fun getResourceType(): Class<Task> {
        return Task::class.java
    }


    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam task: Task,
        @IdParam theId: IdType?,
        @ConditionalUrlParam theConditional : String?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {


        return awsTask.createUpdate(task)

    }
    
    @Search
    fun search(httpRequest : HttpServletRequest,
               @OptionalParam(name = Task.SP_OWNER) owner: ReferenceParam?,
               @OptionalParam(name = Task.SP_REQUESTER) requester: ReferenceParam?,
               @OptionalParam(name = Task.SP_PATIENT) patient : ReferenceParam?,
               @OptionalParam(name= Task.SP_STATUS) status : TokenParam?,
               @OptionalParam(name= Task.SP_CODE) code : TokenParam?
    ): List<Task> {
        val list = mutableListOf<Task>()
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, httpRequest.queryString)
        if (resource != null && resource is Bundle) {
            for (entry in resource.entry) {
                if (entry.hasResource() && entry.resource is Task) list.add(entry.resource as Task)
            }
        }
        return list
    }

}
