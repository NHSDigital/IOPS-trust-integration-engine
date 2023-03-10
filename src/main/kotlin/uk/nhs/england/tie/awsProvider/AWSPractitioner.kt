package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.configuration.MessageProperties

@Component
class AWSPractitioner(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                      @Qualifier("R4") val ctx: FhirContext,
                      val fhirServerProperties: FHIRServerProperties,
                      val awsBundle: AWSBundle,
                      val awsAuditEvent: AWSAuditEvent
) {

    private val log = LoggerFactory.getLogger("FHIRAudit")
    public fun get(identifier: Identifier): Practitioner? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Practitioner::class.java)
                    .where(
                        Practitioner.IDENTIFIER.exactly()
                            .systemAndCode(identifier.system, identifier.value)
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
        if (bundle == null || !bundle.hasEntry()) return null
        return bundle.entryFirstRep.resource as Practitioner
    }

    public fun get(reference: Reference, bundle: Bundle): Practitioner? {
        var awsPractitioner : Practitioner? = null
        if (reference.hasReference()) {
            val practitioner = awsBundle.findResource(bundle, "Practitioner", reference.reference) as Practitioner
            if (practitioner != null) {
                for ( identifier in practitioner.identifier) {
                    awsPractitioner = get(identifier)
                    if (awsPractitioner != null) {
                        break;
                    }
                }
                if (awsPractitioner == null) {
                    return createPractitioner(practitioner,bundle)?.resource as Practitioner
                } else return awsPractitioner
            }
        } else if (reference.hasIdentifier()) {
            return get(reference.identifier)
        }
        return null
    }
    fun createPractitioner(newPractitioner: Practitioner, bundle: Bundle): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newPractitioner)
                    .execute()
                val practitioner = response.resource as Practitioner
                val auditEvent = awsAuditEvent.createAudit(practitioner, AuditEvent.AuditEventAction.C)
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

    fun transform(practitioner: Practitioner): Resource? {
        return practitioner
    }
}
