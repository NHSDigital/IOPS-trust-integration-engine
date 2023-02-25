package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSTask
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
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

    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam task: Task): MethodOutcome? {

        val method = MethodOutcome().setCreated(true)
        method.resource = awsTask.createUpdate(task, null)
        return method
    }

}
