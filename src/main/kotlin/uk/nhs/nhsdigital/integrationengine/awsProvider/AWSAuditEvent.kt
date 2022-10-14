package uk.nhs.nhsdigital.integrationengine.awsProvider

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.r4.model.AuditEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class AWSAuditEvent(@Qualifier("R4") val ctx: FhirContext) {

    private val log = LoggerFactory.getLogger("FHIRAudit")

    public fun writeAWS(event: AuditEvent) {
        val audit = ctx!!.newJsonParser().encodeResourceToString(event)
        if (event.hasOutcome() && event.outcome != AuditEvent.AuditEventOutcome._0) {
            log.error(audit)
        } else {
            log.info(audit)
        }
        /* Add back in at later date
        val send_msg_request = SendMessageRequest()
            .withQueueUrl(sqs.getQueueUrl(MessageProperties.getAwsQueueName()).getQueueUrl())
            .withMessageBody(audit)
            .withDelaySeconds(5)
        sqs!!.sendMessage(send_msg_request)

         */
    }
}
