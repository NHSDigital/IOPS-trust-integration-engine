package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome

import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSQuestionnaireResponse


import javax.servlet.http.HttpServletRequest

@Component
class QuestionnaireResponseProvider(
                                   var awsQuestionnaireResponse: AWSQuestionnaireResponse

) : IResourceProvider {
    override fun getResourceType(): Class<QuestionnaireResponse> {
        return QuestionnaireResponse::class.java
    }

    @Create
    fun create(
        theRequest: HttpServletRequest,
        @ResourceParam questionnaireResponse: QuestionnaireResponse
    ): MethodOutcome? {
        return awsQuestionnaireResponse.createUpdate(questionnaireResponse)
    }


}
