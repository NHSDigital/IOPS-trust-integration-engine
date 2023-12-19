package uk.nhs.england.tie.component

import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import java.util.*

class FhirBundleUtil internal constructor(value: Bundle.BundleType?) {
    // THIS IS A LIBRARY CLASS !!!BUT!!! IS CURRENTLY IN SEVERAL MODULES
    // BE CAREFUL AND UPDATE ALL VERSIONS.
    //
    // This will be moved to a central library when development is at that stage
    var fhirDocument: Bundle = Bundle()
        .setType(value)
    var patient: Patient? = null
    var referenceMap: MutableMap<String, String?> = HashMap()
    val uuidtag = "urn:uuid:"

    init {
        fhirDocument.getIdentifier().setValue(UUID.randomUUID().toString())
            .setSystem("https://tools.ietf.org/html/rfc4122")
    }



    fun processReferences() {
        for (entry in fhirDocument!!.entry) {
            if (entry.resource is AllergyIntolerance) {
                val allergyIntolerance = entry.resource as AllergyIntolerance
                allergyIntolerance.setPatient(getUUIDReference(allergyIntolerance.patient))
            }
            if (entry.resource is Condition) {
                val condition = entry.resource as Condition
                condition.setSubject(getUUIDReference(condition.subject))
                if (condition.hasEncounter()) {
                    condition.setEncounter(getUUIDReference(condition.encounter))
                }
            }
            if (entry.resource is Composition) {
                val composition = entry.resource as Composition
                if (composition.hasSubject()) composition.setSubject(getUUIDReference(composition.subject))
                for (attester in composition.attester) {
                    if (attester.hasParty()) {
                        attester.setParty(getUUIDReference(attester.party))
                    }
                }
                for (reference in composition.author) {
                    reference.setReference(getUUIDReference(reference)!!.reference)
                }
            }
            if (entry.resource is DocumentReference) {
                val documentReference = entry.resource as DocumentReference
                if (documentReference.hasSubject()) {
                    documentReference.setSubject(getUUIDReference(documentReference.subject))
                }
                if (documentReference.hasContent()) {
                    for (content in documentReference.content) if (content.hasAttachment()) {
                        log.debug("Attachment url = " + content.attachment.url)
                        content.attachment.setUrl(uuidtag + getNewReferenceUri(content.attachment.url))
                    }
                }
                if (documentReference.hasCustodian()) {
                    // log.info("Bundle Custodian Ref = "+documentReference.getCustodian().getReference());
                    documentReference.setCustodian(getUUIDReference(documentReference.custodian))
                }
                for (reference in documentReference.author) {
                    reference.setReference(getUUIDReference(reference)!!.reference)
                }
            }
            if (entry.resource is Device) {
                val device = entry.resource as Device
                if (device.hasOwner()) {
                    device.setOwner(getUUIDReference(device.owner))
                }
            }
            if (entry.resource is Encounter) {
                val encounter = entry.resource as Encounter
                encounter.setSubject(Reference(uuidtag + patient!!.id))
                if (encounter.hasServiceProvider() && encounter.serviceProvider.reference != null) {
                    encounter.setServiceProvider(getUUIDReference(encounter.serviceProvider))
                } else {
                    encounter.setServiceProvider(null)
                }
                val newReferences: MutableList<Reference> = ArrayList()
                for (reference in encounter.episodeOfCare) {
                    val newReference = getUUIDReference(reference)
                    if (newReference != null) {
                        newReferences.add(newReference)
                    }
                }
                encounter.setEpisodeOfCare(newReferences)
                for (locationComponent in encounter.location) {
                    locationComponent.setLocation(getUUIDReference(locationComponent.location))
                }
                for (participantComponent in encounter.participant) {
                    if (participantComponent.hasIndividual()) participantComponent.setIndividual(
                        getUUIDReference(
                            participantComponent.individual
                        )
                    )
                }
            }
            if (entry.resource is Observation) {
                val observation = entry.resource as Observation
                observation.setSubject(Reference(uuidtag + patient!!.id))
                if (observation.hasEncounter()) {
                    observation.setEncounter(getUUIDReference(observation.encounter))
                }
            }
            if (entry.resource is MedicationRequest) {
                val medicationRequest = entry.resource as MedicationRequest
                if (medicationRequest.hasEncounter()) {
                    medicationRequest.setEncounter(getUUIDReference(medicationRequest.encounter))
                }
                medicationRequest.setSubject(Reference(uuidtag + patient!!.id))
                if (medicationRequest.hasRecorder()) medicationRequest.setRecorder(getUUIDReference(medicationRequest.recorder))
                try {
                    if (medicationRequest.hasMedicationReference()) medicationRequest.setMedication(
                        getUUIDReference(
                            medicationRequest.medicationReference
                        )
                    )
                } catch (ex: Exception) {
                }
            }
            if (entry.resource is MedicationStatement) {
                val medicationStatement = entry.resource as MedicationStatement
                medicationStatement.setSubject(Reference(uuidtag + patient!!.id))
                if (medicationStatement.hasContext()) {
                    medicationStatement.setContext(getUUIDReference(medicationStatement.context))
                }
            }
            if (entry.resource is Organization) {
                val organization = entry.resource as Organization
                if (organization.hasPartOf()) {
                    if (getNewReferenceUri(organization.partOf.reference) != null) {
                        organization.setPartOf(getUUIDReference(organization.partOf))
                    } else {
                        organization.setPartOf(null)
                    }
                }
            }
            if (entry.resource is Patient) {
                val patient = entry.resource as Patient
                if (patient.hasManagingOrganization()) {
                    patient.setManagingOrganization(Reference(uuidtag + getNewReferenceUri(patient.managingOrganization.reference)))
                }
                for (reference in patient.generalPractitioner) {
                    if (reference.reference !== null) patient.generalPractitioner[0].setReference(uuidtag + getNewReferenceUri(reference.reference))
                }
            }
            if (entry.resource is Practitioner) {
                val practitioner = entry.resource as Practitioner
            }
            if (entry.resource is Procedure) {
                val procedure = entry.resource as Procedure
                procedure.setSubject(Reference(uuidtag + patient!!.id))
                if (procedure.hasEncounter()) {
                    procedure.setEncounter(getUUIDReference(procedure.encounter))
                }
            }
        }
    }

    fun processBundleResources(bundle: Bundle) {
        val gp: Practitioner? = null
        val practice: Organization? = null
        for (entry in bundle.entry) {
            val resource = entry.resource
            resource.setId(getNewId(resource))
            fhirDocument!!.addEntry().setResource(entry.resource).setFullUrl(uuidtag + resource.id)
            if (entry.resource is Patient) {
                patient = entry.resource as Patient
            }
        }
    }

    private fun getUUIDReference(reference: Reference): Reference? {
        if (referenceMap[reference.reference] == null) {
            log.error("Missing reference " + reference.reference)
        }
        return if (reference.reference == getNewReferenceUri(reference.reference)) {
            reference
        } else {
            val UUIDReference = getNewReferenceUri(reference.reference)
            if (UUIDReference != null) {
                Reference(uuidtag + UUIDReference)
            } else {
                null
            }
        }
    }

    private fun getNewReferenceUri(resource: Resource): String? {
        return getNewReferenceUri(resource.resourceType.toString() + "/" + resource.id)
    }

    fun getNewId(resource: Resource): String {
        var reference = resource.id
        var newReference: String? = null
        if (reference != null) {
            newReference = referenceMap[reference]
            if (newReference != null) return newReference
        }
        newReference = UUID.randomUUID().toString()
        if (reference == null) {
            reference = newReference
            referenceMap[resource.javaClass.getSimpleName() + "/" + reference] = newReference
        } else {
            log.info(resource.javaClass.getSimpleName() + "/" + resource.idElement.idPart + " [-] " + newReference)
            referenceMap[resource.javaClass.getSimpleName() + "/" + resource.idElement.idPart] = newReference
        }
        log.debug("$reference [-] $newReference")
        referenceMap[reference] = newReference
        referenceMap[newReference] = newReference // Add in self
        referenceMap[uuidtag + newReference] = newReference // Add in self
        return newReference
    }

    private fun getNewReferenceUri(reference: String): String? {
        if (reference.contains(uuidtag)) {
            return reference.replace(uuidtag, "")
        }
        val newReference = referenceMap[reference]
        if (newReference != null) return newReference
        log.info("Missing newReference for $reference")
        return newReference
    }

    companion object {
        private val log = LoggerFactory.getLogger(FhirBundleUtil::class.java)
    }
}
