package uk.nhs.nhsdigital.pmir.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.nhsdigital.pmir.configuration.FHIRServerProperties
import uk.nhs.nhsdigital.pmir.configuration.MessageProperties
import java.util.*

@Component
class AWSDiagnosticReport(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                          @Qualifier("R4") val ctx: FhirContext,
                          val fhirServerProperties: FHIRServerProperties,
                          val awsOrganization: AWSOrganization,
                          val awsBundleProvider : AWSBundle,
                          val awsPractitionerRole: AWSPractitionerRole,
                          val awsPractitioner: AWSPractitioner,
                          val awsPatient: AWSPatient,
                          val awsObservation: AWSObservation,
                          val awsAuditEvent: AWSAuditEvent) {

    private val log = LoggerFactory.getLogger("FHIRAudit")


    fun createUpdate(newDiagnosticReport: DiagnosticReport, bundle: Bundle?, operationOutcome: OperationOutcome): DiagnosticReport? {
        var awsBundle: Bundle? = null
        if (!newDiagnosticReport.hasIdentifier()) throw UnprocessableEntityException("DiagnosticReport has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newDiagnosticReport.identifier) {
                if (identifier.system != null && identifier.value != null) nhsIdentifier = identifier
        }
        if (nhsIdentifier == null) throw UnprocessableEntityException("DiagnosticReport has no identifier")
        var retry = 3
        while (retry > 0) {
            try {

                awsBundle = awsClient!!.search<IBaseBundle>().forResource(DiagnosticReport::class.java)
                    .where(
                        DiagnosticReport.IDENTIFIER.exactly()
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

        if (newDiagnosticReport.hasSubject()) {
            if (newDiagnosticReport.subject.hasReference() && bundle != null) {
                val patient = awsPatient.getPatient(newDiagnosticReport.subject, bundle)
                if (patient != null) awsBundleProvider.updateReference(newDiagnosticReport.subject, patient.identifierFirstRep,patient)
            } else
                if (newDiagnosticReport.subject.hasIdentifier()) {
                    val patient = awsPatient.getPatient(newDiagnosticReport.subject.identifier)
                    if (patient != null) awsBundleProvider.updateReference(newDiagnosticReport.subject, patient.identifierFirstRep,patient)
                }
        }
        if (newDiagnosticReport.hasPerformer() && bundle != null) {
            for (performer in newDiagnosticReport.performer) {
                if (performer.resource != null) {
                    if (performer.resource is PractitionerRole) {
                        val practitionerRole = awsPractitionerRole.get(performer,bundle)
                        if (practitionerRole != null) awsBundleProvider.updateReference(performer, practitionerRole.identifierFirstRep, practitionerRole)
                    }
                    if (performer.resource is Practitioner) {
                        val practitioner = awsPractitioner.get(performer,bundle)
                        if (practitioner != null) awsBundleProvider.updateReference(performer, practitioner.identifierFirstRep, practitioner)
                    }
                    if (performer.resource is Organization) {
                        val organisation = awsOrganization.get(performer,bundle)
                        if (organisation != null) awsBundleProvider.updateReference(performer, organisation.identifierFirstRep, organisation)
                    }
                }
            }

        }
        if (newDiagnosticReport.hasResult()) {
            for (result in newDiagnosticReport.result) {
                if (result.resource is Observation) {
                    val observation = awsObservation.createUpdate(result.resource as Observation,bundle)
                    if (observation != null) {
                        awsBundleProvider.updateReference(result, observation.identifierFirstRep, observation)
                        // is also storing observations so return them
                        operationOutcome.issue.add(
                            OperationOutcome.OperationOutcomeIssueComponent()
                                .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                                .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                                .addLocation(observation.id))
                    }
                }
            }
        }
        // This v3esquw data should have been processed into propoer resources so remove
        newDiagnosticReport.contained = ArrayList()

        if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is DiagnosticReport
        ) {
            val diagnosticReport = awsBundle.entryFirstRep.resource as DiagnosticReport
            // Dont update for now - just return aws DiagnosticReport
            return updateDiagnosticReport(diagnosticReport, newDiagnosticReport)!!.resource as DiagnosticReport
        } else {
            return createDiagnosticReport(newDiagnosticReport)!!.resource as DiagnosticReport
        }
    }

    public fun getDiagnosticReport(identifier: Identifier): DiagnosticReport? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(DiagnosticReport::class.java)
                    .where(
                        DiagnosticReport.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as DiagnosticReport
    }

    private fun updateDiagnosticReport(diagnosticReport: DiagnosticReport, newDiagnosticReport: DiagnosticReport): MethodOutcome? {
        var response: MethodOutcome? = null
        var changed: Boolean
        for (identifier in newDiagnosticReport.identifier) {
            var found = false
            for (awsidentifier in diagnosticReport.identifier) {
                if (awsidentifier.value == identifier.value && awsidentifier.system == identifier.system) {
                    found = true
                }
            }
            if (!found) {
                diagnosticReport.addIdentifier(identifier)
            }
        }

        // TODO do change detection
        changed = true

        if (!changed) return MethodOutcome().setResource(diagnosticReport)
        var retry = 3
        while (retry > 0) {
            try {
                newDiagnosticReport.id = diagnosticReport.idElement.value
                response = awsClient!!.update().resource(newDiagnosticReport).withId(diagnosticReport.id).execute()
                log.info("AWS DiagnosticReport updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(diagnosticReport, AuditEvent.AuditEventAction.C)
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

    private fun createDiagnosticReport(newDiagnosticReport: DiagnosticReport): MethodOutcome? {

        var response: MethodOutcome? = null

        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newDiagnosticReport)
                    .execute()
                val diagnosticReport = response.resource as DiagnosticReport
                val auditEvent = awsAuditEvent.createAudit(diagnosticReport, AuditEvent.AuditEventAction.C)
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
