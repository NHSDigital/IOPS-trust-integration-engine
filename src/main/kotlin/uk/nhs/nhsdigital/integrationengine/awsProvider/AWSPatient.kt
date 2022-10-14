package uk.nhs.nhsdigital.integrationengine.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Organization
import org.hl7.fhir.r4.model.Patient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.integrationengine.configuration.FHIRServerProperties
import uk.nhs.nhsdigital.integrationengine.configuration.MessageProperties
import uk.nhs.nhsdigital.integrationengine.util.FhirSystems
import java.util.*

@Component
class AWSPatient (val messageProperties: MessageProperties, val awsClient: IGenericClient,
               //sqs: AmazonSQS?,
                  @Qualifier("R4") val ctx: FhirContext,
                  val fhirServerProperties: FHIRServerProperties) {


    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdateAWSPatient(newPatient: Patient): Patient? {
        var awsBundle: Bundle? = null
        if (!newPatient.hasIdentifier()) throw UnprocessableEntityException("Patient has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newPatient.identifier) {
            if (identifier.system == FhirSystems.NHS_NUMBER) {
                nhsIdentifier = identifier
                break
            }
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("Patient has no NHS Number identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(Patient::class.java)
                    .where(
                        Patient.IDENTIFIER.exactly()
                            .systemAndCode(nhsIdentifier.system, nhsIdentifier.value)
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
        return if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Patient
        ) {
            val patient = awsBundle.entryFirstRep.resource as Patient
            // Dont update for now - just return aws Patient
            updatePatient(patient, newPatient)!!.resource as Patient
        } else {
            createPatient(newPatient)!!.resource as Patient
        }
    }

    fun updatePatient(patient: Patient, newPatient: Patient): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed = false
        for (identifier in newPatient.identifier) {
            var found = false
            for (awsidentifier in patient.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                patient.addIdentifier(identifier)
                changed = true
            }
        }
        if (newPatient.hasGeneralPractitioner()) {
            var surgery : Organization? = null
            for (practitioner in newPatient.generalPractitioner) {
                if (practitioner.hasIdentifier() && practitioner.identifier.system.equals(FhirSystems.ODS_CODE)) {
                    surgery = getOrganization(practitioner.identifier)
                    if (surgery != null) practitioner.reference = "Organization/" + surgery.idElement.idPart
                }
            }
            if (!patient.hasGeneralPractitioner()) {
                changed = true
            } else if (surgery != null && (!patient.generalPractitionerFirstRep.hasReference() || !patient.generalPractitionerFirstRep.reference.contains(
                    surgery.idElement.idPart
                ) ||
                patient.generalPractitionerFirstRep.reference != "Organization/" + surgery.idElement.idPart
            )) {
                changed = true


            }
        } else {
            throw UnprocessableEntityException("EMIS patient must have a general practitioner")
        }
        if (!changed) return MethodOutcome().setResource(patient)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient!!.update().resource(patient).withId(patient.id).execute()
                log.info("AWS Patient updated " + response.resource.idElement.value)
                val auditEvent = createAudit(patient, AuditEvent.AuditEventAction.C)
                writeAWS(auditEvent)
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

    fun createPatient(newPatient: Patient): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null
        if (!newPatient.hasGeneralPractitioner() || !newPatient.generalPractitionerFirstRep.hasIdentifier()) throw UnprocessableEntityException(
            "Practitioner is required"
        )
        for (practitioner in newPatient.generalPractitioner) {
            if (practitioner.hasIdentifier() && practitioner.identifier.system.equals(FhirSystems.ODS_CODE)) {
                val surgery = getOrganization(practitioner.identifier)
                if (surgery != null) practitioner.reference = "Organization/" + surgery.idElement.idPart
            }
        }

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newPatient)
                    .execute()
                val patient = response.resource as Patient
                val auditEvent = createAudit(patient, AuditEvent.AuditEventAction.C)
                writeAWS(auditEvent)
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

    private fun getOrganization(identifier: Identifier): Organization? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Organization::class.java)
                    .where(
                        Organization.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Organization
    }

    fun createAudit(patient: Patient, auditEventAction: AuditEvent.AuditEventAction?): AuditEvent {
        val auditEvent = AuditEvent()
        auditEvent.recorded = Date()
        auditEvent.action = auditEventAction

        // Entity
        val entityComponent = auditEvent.addEntity()
        var path: String? = messageProperties.getCdrFhirServer()
        path += "/Patient"
        auditEvent.outcome = AuditEvent.AuditEventOutcome._0
        entityComponent.addDetail().setType("query").value = StringType(path)
        auditEvent.type = Coding().setSystem(FhirSystems.ISO_EHR_EVENTS).setCode("transmit")
        entityComponent.addDetail().setType("resource").value = StringType("Patient")
        entityComponent.type = Coding().setSystem(FhirSystems.FHIR_RESOURCE_TYPE).setCode("Patient")
        auditEvent.source.observer = Reference()
            .setIdentifier(Identifier().setValue(fhirServerProperties.server.baseUrl))
            .setDisplay((fhirServerProperties.server.name + " " + fhirServerProperties.server.version).toString() + " " + fhirServerProperties.server.baseUrl)
            .setType("Device")


        // Agent Application
        val agentComponent = auditEvent.addAgent()
        agentComponent.requestor = true
        agentComponent.type = CodeableConcept(Coding().setSystem(FhirSystems.DICOM_AUDIT_ROLES).setCode("110150"))

        /// Agent Patient about
        val patientAgent = auditEvent.addAgent()
        patientAgent.requestor = false
        patientAgent.type =
            CodeableConcept(Coding().setSystem(FhirSystems.V3_ROLE_CLASS).setCode("PAT"))
        patientAgent.who = Reference().setType("Patient")
            .setReference("Patient/" + patient.idElement.idPart)
        return auditEvent
    }

    fun writeAWS(event: AuditEvent) {
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
