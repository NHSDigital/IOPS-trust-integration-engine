package uk.nhs.nhsdigital.pmir.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.server.IResourceProvider
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.pmir.awsProvider.AWSSubscription
import uk.nhs.nhsdigital.pmir.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class SubscriptionProvider(
                           var awsSubscription: AWSSubscription) : IResourceProvider {
    override fun getResourceType(): Class<Subscription> {
        return Subscription::class.java
    }

    @Read
    fun read(theRequest: HttpServletRequest, @IdParam theId: IdType): Subscription? {

        return awsSubscription.read(theId)

    }
    @Delete
    fun delate(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {

        return awsSubscription.delete(theId)

    }

    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam patient: Subscription,
        @IdParam theId: IdType?,
        @ConditionalUrlParam theConditional : String?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {

        var method = MethodOutcome().setCreated(true)
        if (theId != null) return awsSubscription.update(patient, theId)
        return method
    }
    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam subscription: Subscription): MethodOutcome? {

        var method = MethodOutcome().setCreated(true)
       return awsSubscription.create(subscription)
        return method
    }

}
