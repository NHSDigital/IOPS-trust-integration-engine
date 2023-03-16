package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Organization
import org.hl7.fhir.r4.model.Patient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.configuration.MessageProperties
import uk.nhs.england.tie.util.FhirSystems
import java.nio.charset.StandardCharsets
import java.util.*

@Component
class AWSPatient (val messageProperties: MessageProperties, val awsClient: IGenericClient,
               //sqs: AmazonSQS?,
                  @Qualifier("R4") val ctx: FhirContext,
                  val fhirServerProperties: FHIRServerProperties,
                  val awsOrganization: AWSOrganization,
                  val awsPractitioner: AWSPractitioner,
                  val awsBundleProvider: AWSBundle,
                  val awsAuditEvent: AWSAuditEvent) {


    private val log = LoggerFactory.getLogger("FHIRAudit")

    fun processQueryString(httpString: String?, nhsNumber : TokenParam? ) : String? {
        var queryString: String? = httpString
        if (queryString != null) {
            val params: List<String> = queryString.split("&")
            val newParams = mutableListOf<String>()
            var patient : Patient? = null
            if (nhsNumber != null) {
                if (nhsNumber.value == null || nhsNumber.system == null) throw UnprocessableEntityException("Malformed patient identifier parameter both system and value are required.")
                patient = get(Identifier().setSystem(nhsNumber.system).setValue(nhsNumber.value))
            }

            for (param in params) {
                val name: String = param.split("=").get(0)
                val value: String = param.split("=").get(1)
                if (patient != null && java.net.URLDecoder.decode(name, StandardCharsets.UTF_8.name()).equals("patient:identifier")) {
                    newParams.add( "patient=" + patient.idElement.idPart)
                } else if (name.equals("_content")) {
                    newParams.add("title=$value")
                } else if (name.equals("_total")) {
                    //newParams.add("title=$value")
                }
                else {
                    newParams.add(param)
                }
            }
            queryString = newParams.joinToString("&")
        }
        return queryString
    }

    fun createUpdate(newPatient: Patient, bundle: Bundle?): Patient? {
        var awsBundle: Bundle? = null
        if (!newPatient.hasIdentifier()) throw UnprocessableEntityException("Patient has no identifier")
        var nhsIdentifier: Identifier? = null
        for (identifier in newPatient.identifier) {
            // This was a NHS Number check but this has been removed to allow to for more flexible demonstrations
           // if (identifier.system == FhirSystems.NHS_NUMBER) {
                nhsIdentifier = identifier
                break

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
        // Update references
        if (newPatient.hasGeneralPractitioner()) {
            var surgery : Organization? = null
            var practitioner : Practitioner? = null
            for (generalPractitioner in newPatient.generalPractitioner) {
                if (generalPractitioner.hasIdentifier() ) {
                    if (generalPractitioner.identifier.system.equals(FhirSystems.ODS_CODE)) {
                        surgery = awsOrganization.get(generalPractitioner.identifier)
                        if (surgery != null) awsBundleProvider.updateReference(generalPractitioner, surgery.identifierFirstRep, surgery)
                    }
                    if (generalPractitioner.identifier.system.equals(FhirSystems.NHS_GMP_NUMBER) || generalPractitioner.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)) {
                        practitioner = awsPractitioner.get(generalPractitioner.identifier)
                        if (practitioner != null) awsBundleProvider.updateReference(generalPractitioner, practitioner.identifierFirstRep, practitioner)
                    }
                }
            }
        }
        if (newPatient.hasLink()) {
            for(linkedPatient in newPatient.link) {
                if (linkedPatient.hasOther() && linkedPatient.other.hasIdentifier()) {
                    var awsPatient = get(linkedPatient.other.identifier)
                    if (awsPatient != null) awsBundleProvider.updateReference(linkedPatient.other,linkedPatient.other.identifier,awsPatient)
                }
            }
        }

        return if (awsBundle!!.hasEntry() && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.hasResource()
            && awsBundle.entryFirstRep.resource is Patient
        ) {
            val patient = awsBundle.entryFirstRep.resource as Patient
            // Dont update for now - just return aws Patient
            return update(patient, newPatient)!!.resource as Patient
        } else {
            return create(newPatient)!!.resource as Patient
        }
    }

    fun update(patient: Patient, newPatient: Patient): MethodOutcome? {
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

        // TODO do change detection
        changed = true;

        if (!changed) return MethodOutcome().setResource(patient)
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient!!.update().resource(newPatient).withId(patient.id).execute()
                log.info("AWS Patient updated " + response.resource.idElement.value)
                val auditEvent = awsAuditEvent.createAudit(patient, AuditEvent.AuditEventAction.C)
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

    public fun get(identifier: Identifier): Patient? {
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(Patient::class.java)
                    .where(
                        Patient.IDENTIFIER.exactly()
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
        return bundle.entryFirstRep.resource as Patient
    }
    public fun get(reference: Reference, bundle: Bundle): Patient? {
        var awsPatient : Patient? = null
        if (reference.hasReference()) {
            val resource = awsBundleProvider.findResource(bundle, "Patient", reference.reference)
            if (resource != null && resource is Patient) {
                val patient = resource
                for (identifier in patient.identifier) {
                    if (identifier.system.equals(FhirSystems.NHS_NUMBER)) {
                        awsPatient = get(identifier)
                        if (awsPatient == null) {
                            return create(patient)?.resource as Patient
                        } else {
                            return awsPatient
                        }
                    }
                }
            }
        } else if (reference.hasIdentifier()) {
            return get(reference.identifier)
        }
        return null
    }

    fun create(newPatient: Patient): MethodOutcome? {
        val awsBundle: Bundle? = null
        var response: MethodOutcome? = null


        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newPatient)
                    .execute()
                val patient = response.resource as Patient
                val auditEvent = awsAuditEvent.createAudit(patient, AuditEvent.AuditEventAction.C)
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

    fun transform(newPatient: Patient): Resource? {
        if (newPatient.hasGeneralPractitioner()) {
            var surgery : Organization? = null
            var practitioner : Practitioner? = null
            for (generalPractitioner in newPatient.generalPractitioner) {
                if (generalPractitioner.hasIdentifier() ) {
                    if (generalPractitioner.identifier.system.equals(FhirSystems.ODS_CODE)) {
                        surgery = awsOrganization.get(generalPractitioner.identifier)
                        if (surgery != null) awsBundleProvider.updateReference(generalPractitioner, surgery.identifierFirstRep, surgery)
                    }
                    if (generalPractitioner.identifier.system.equals(FhirSystems.NHS_GMP_NUMBER) || generalPractitioner.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)) {
                        practitioner = awsPractitioner.get(generalPractitioner.identifier)
                        if (practitioner != null) awsBundleProvider.updateReference(generalPractitioner, practitioner.identifierFirstRep, practitioner)
                    }
                }
            }
        }
        if (newPatient.hasLink()) {
            for(linkedPatient in newPatient.link) {
                if (linkedPatient.hasOther() && linkedPatient.other.hasIdentifier()) {
                    var awsPatient = get(linkedPatient.other.identifier)
                    if (awsPatient != null) awsBundleProvider.updateReference(linkedPatient.other,linkedPatient.other.identifier,awsPatient)
                }
            }
        }
        return newPatient
    }


}
