package uk.nhs.england.tie.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import mu.KLogging
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSQuestionnaire
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class QuestionnaireProvider (@Qualifier("R4") private val fhirContext: FhirContext,
                             private val cognitoAuthInterceptor: CognitoAuthInterceptor,
                             private val awsQuestionnaire: AWSQuestionnaire
) :IResourceProvider {
    /**
     * The getResourceType method comes from IResourceProvider, and must
     * be overridden to indicate what type of resource this provider
     * supplies.
     */

    override fun getResourceType(): Class<Questionnaire> {
        return Questionnaire::class.java
    }

    companion object : KLogging()



    @Read
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): Questionnaire? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"Questionnaire")
        return if (resource is Questionnaire) resource else null
    }

    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam questionnaire: Questionnaire,
        @IdParam theId: IdType,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        return awsQuestionnaire.update(questionnaire, theId)
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam questionnaire: Questionnaire): MethodOutcome? {
        return awsQuestionnaire.create(questionnaire)
    }

    @Delete
    fun create(theRequest: HttpServletRequest,  @IdParam theId: IdType): MethodOutcome? {
        return awsQuestionnaire.delete(theId)
    }
}
