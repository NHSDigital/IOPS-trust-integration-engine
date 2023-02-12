package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.gclient.ICriterion
import ca.uhn.fhir.rest.gclient.ReferenceClientParam
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.param.UriParam
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*

import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.configuration.MessageProperties
import java.nio.charset.StandardCharsets
import java.util.*

@Component
class AWSQuestionnaireResponse (val messageProperties: MessageProperties, val awsClient: IGenericClient,
               //sqs: AmazonSQS?,
                                @Qualifier("R4") val ctx: FhirContext,
                                val fhirServerProperties: FHIRServerProperties,
                                val awsPatient: AWSPatient,
                                val awsOrganization: AWSOrganization,
                                val awsPractitioner: AWSPractitioner,
                                val awsBundleProvider: AWSBundle,
                                val awsQuestionnaire: AWSQuestionnaire,
                                val awsAuditEvent: AWSAuditEvent) {


    private val log = LoggerFactory.getLogger("FHIRAudit")

    fun seach(
       patient: ReferenceParam?,
       questionnaire : ReferenceParam?,
       status : TokenParam?
    ): List<QuestionnaireResponse>? {
        var awsBundle: Bundle? = null
        val list = mutableListOf<QuestionnaireResponse>()

        // if (uriParam == null || uriParam.value == null) throw UnprocessableEntityException("url parameter is mandatory")
        var retry = 3
        while (retry > 0) {
            try {

                var criteria1 :ICriterion<ReferenceClientParam>? = null
                var criteria2 :ICriterion<ReferenceClientParam>? = null

                if (questionnaire != null) {
                    // Need to search registry for Questionnaire id
                    val listQ = awsQuestionnaire.search(UriParam().setValue(questionnaire.value))
                    if (listQ == null || listQ.size==0) return list
                    criteria1 = QuestionnaireResponse.QUESTIONNAIRE.hasId("Questionnaire/"+listQ[0].idElement.idPart)
                }
                if (patient != null) {
                    val criteriaPat = QuestionnaireResponse.PATIENT.hasId(java.net.URLDecoder.decode(patient.value, StandardCharsets.UTF_8.name()))
                    if (criteria1 == null) {
                        criteria1 = criteriaPat
                    } else {
                        criteria2 = criteriaPat
                    }
                }

                if (criteria1 == null) {
                    awsBundle = awsClient!!.search<IBaseBundle>().forResource(QuestionnaireResponse::class.java)
                        .returnBundle(Bundle::class.java)
                        .execute()
                } else {
                    if (criteria2 != null) {
                        awsBundle = awsClient!!.search<IBaseBundle>().forResource(QuestionnaireResponse::class.java)
                            .where(
                                criteria1
                            )
                            .and(criteria2)
                            .returnBundle(Bundle::class.java)
                            .execute()
                    } else {
                        awsBundle = awsClient!!.search<IBaseBundle>().forResource(QuestionnaireResponse::class.java)
                            .where(
                                criteria1
                            )
                            .returnBundle(Bundle::class.java)
                            .execute()
                    }
                }
                break
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        if (awsBundle != null) {
            if (awsBundle.hasEntry() ) {
                for (entry in awsBundle.entry) {
                    if (entry.hasResource() && entry.resource is QuestionnaireResponse) {
                        list.add(entry.resource as QuestionnaireResponse)
                    }
                }
            }
        }
        return list
    }
   
    fun create(newQuestionnaireResponse: QuestionnaireResponse): MethodOutcome? {
        var awsBundle: Bundle? = null
        var response: MethodOutcome? = null
        if (!newQuestionnaireResponse.hasIdentifier()) throw UnprocessableEntityException("QuestionnaireResponse has no identifier")


        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(QuestionnaireResponse::class.java)
                    .where(
                        QuestionnaireResponse.IDENTIFIER.exactly()
                            .systemAndCode(newQuestionnaireResponse.identifier.system, newQuestionnaireResponse.identifier.value)
                    )
                    .returnBundle(Bundle::class.java)
                    .execute()
                break
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        if (awsBundle != null) {
            if (awsBundle.hasEntry() && awsBundle.entry.size>0) {
                throw UnprocessableEntityException("QuestionnaireResponse already exists")
            }
        }

        /*
       if (newQuestionnaireResponse.hasQuestionnaire()) {
           val listQ = awsQuestionnaire.seach(UriParam().setValue(newQuestionnaireResponse.questionnaire))
           if (listQ != null && listQ.size>0) {
               newQuestionnaireResponse.questionnaire = listQ[0].id
           }
       }

        */
        if (newQuestionnaireResponse.hasSource()) {

            // Bit crude refactor?

            if (newQuestionnaireResponse.source.hasIdentifier()) {
                val awsPatient = awsPatient.get(newQuestionnaireResponse.source.identifier)
                if (awsPatient != null)  {
                    awsBundleProvider.updateReference(newQuestionnaireResponse.source,awsPatient.identifierFirstRep,awsPatient)
                } else {
                    val awsOrganization = awsOrganization.get(newQuestionnaireResponse.source.identifier)
                    if (awsOrganization != null) {
                        awsBundleProvider.updateReference(newQuestionnaireResponse.source,awsOrganization.identifierFirstRep,awsOrganization)
                    } else {
                        val awsPractitioner = awsPractitioner.get(newQuestionnaireResponse.source.identifier)
                        if (awsPractitioner != null) {
                            awsBundleProvider.updateReference(newQuestionnaireResponse.source,awsPractitioner.identifierFirstRep,awsPractitioner)
                        }
                    }
                }

            }
        }

        if (newQuestionnaireResponse.hasSubject()) {
            if (newQuestionnaireResponse.subject.hasIdentifier()) {
                val awsPatient = awsPatient.get(newQuestionnaireResponse.subject.identifier)
                if (awsPatient != null)  awsBundleProvider.updateReference(newQuestionnaireResponse.subject,awsPatient.identifierFirstRep,awsPatient)
            }
        }

        retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newQuestionnaireResponse)
                    .execute()
                val communicationRequest = response.resource as QuestionnaireResponse
                val auditEvent = awsAuditEvent.createAudit(communicationRequest, AuditEvent.AuditEventAction.C)
                awsAuditEvent.writeAWS(auditEvent)
                break
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return response
    }


}
