package uk.nhs.england.tie.component

import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.xhtml.XhtmlNode
import org.hl7.fhir.utilities.xhtml.XhtmlParser
import org.slf4j.LoggerFactory
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import uk.nhs.england.tie.util.FhirSystems

class FhirDocUtil internal constructor(private val templateEngine: TemplateEngine) {
    var ctxThymeleaf = Context()
    private val xhtmlParser = XhtmlParser()
    fun getConditionSection(bundle: Bundle): Composition.SectionComponent {
        val section = Composition.SectionComponent()
        val conditions = ArrayList<Condition>()
        section.code
            .addCoding(
                Coding().setSystem(FhirSystems.LOINC)
                    .setCode("11450-4")
            )
            .addCoding(
                Coding().setSystem(FhirSystems.SNOMED_CT)
                    .setCode("887151000000100")
                    .setDisplay("Problems and issues")
            )
        section.setTitle("Problems and issues")
        for (entry in bundle.entry) {
            if (entry.resource is Condition) {
                val condition = entry.resource as Condition
                section.entry.add(Reference("urn:uuid:" + condition.id))
                conditions.add(condition)
            }
        }
        ctxThymeleaf.clearVariables()
        ctxThymeleaf.setVariable("conditions", conditions)
        section.text.setDiv(getDiv("condition")).setStatus(Narrative.NarrativeStatus.GENERATED)
        return section
    }

    fun getAllergySection(bundle: Bundle): Composition.SectionComponent {
        val section = Composition.SectionComponent()
        val allergyIntolerances = ArrayList<AllergyIntolerance>()
        section.code
            .addCoding(
                Coding().setSystem(FhirSystems.LOINC)
                    .setCode("48765-2")
            )
            .addCoding(
                Coding().setSystem(FhirSystems.SNOMED_CT)
                    .setCode("886921000000105")
                    .setDisplay("Allergies and adverse reactions")
            )
        section.setTitle("Allergies and adverse reactions")
        for (entry in bundle.entry) {
            if (entry.resource is AllergyIntolerance) {
                val allergyIntolerance = entry.resource as AllergyIntolerance
                section.entry.add(Reference("urn:uuid:" + allergyIntolerance.id))
                allergyIntolerances.add(allergyIntolerance)
            }
        }
        ctxThymeleaf.clearVariables()
        ctxThymeleaf.setVariable("allergies", allergyIntolerances)
        section.text.setDiv(getDiv("allergy")).setStatus(Narrative.NarrativeStatus.GENERATED)
        return section
    }

    fun getEncounterSection(bundle: Bundle): Composition.SectionComponent {
        val section = Composition.SectionComponent()
        // TODO Get Correct code.
        val encounters = ArrayList<Encounter>()
        section.code.addCoding()
            .setSystem(FhirSystems.SNOMED_CT)
            .setCode("713511000000103")
            .setDisplay("Encounter administration")
        section.setTitle("Encounters")
        for (entry in bundle.entry) {
            if (entry.resource is Encounter) {
                val encounter = entry.resource as Encounter
                section.entry.add(Reference("urn:uuid:" + encounter.id))
                encounters.add(encounter)
            }
        }
        ctxThymeleaf.clearVariables()
        ctxThymeleaf.setVariable("encounters", encounters)
        section.text.setDiv(getDiv("encounter")).setStatus(Narrative.NarrativeStatus.GENERATED)
        return section
    }

    fun getMedicationsSection(
        medicationsRequests: Bundle,
        medicationsStatements: Bundle
    ): Composition.SectionComponent {
        val section = Composition.SectionComponent()
        val medicationRequests = ArrayList<MedicationRequest>()
        val medicationStatements = ArrayList<MedicationStatement>()
        section.code
            .addCoding(
                Coding().setSystem(FhirSystems.LOINC)
                    .setCode("10160-0")
            )
            .addCoding(
                Coding().setSystem(PatientSummary.SNOMEDCT)
                    .setCode("933361000000108")
                    .setDisplay("Medications and medical devices")
            )
        section.setTitle("Medications and medical devices")
        for (entry in medicationsRequests.entry) {
            if (entry.resource is MedicationRequest) {
                val medicationRequest = entry.resource as MedicationRequest
                //medicationStatement.getMedicationReference().getDisplay();
                section.entry.add(Reference("urn:uuid:" + medicationRequest.id))
                medicationRequest.authoredOn
                medicationRequests.add(medicationRequest)
            }
        }
        for (entry in medicationsStatements.entry) {
            if (entry.resource is MedicationStatement) {
                val medicationStatement = entry.resource as MedicationStatement
                section.entry.add(Reference("urn:uuid:" + medicationStatement.id))
                medicationStatements.add(medicationStatement)
            }
        }
        ctxThymeleaf.clearVariables()
        ctxThymeleaf.setVariable("medicationRequests", medicationRequests)
        ctxThymeleaf.setVariable("medicationStatements", medicationStatements)
        section.text.setDiv(getDiv("medicationRequestAndStatement")).setStatus(Narrative.NarrativeStatus.GENERATED)
        return section
    }

    fun getObservationSection(bundle: Bundle): Composition.SectionComponent {
        val section = Composition.SectionComponent()
        val observations = ArrayList<Observation>()
        section.code.addCoding()
            .setSystem(PatientSummary.SNOMEDCT)
            .setCode("425044008")
            .setDisplay("Physical exam section")
        section.setTitle("Physical exam section")
        for (entry in bundle.entry) {
            if (entry.resource is Observation) {
                val observation = entry.resource as Observation
                section.entry.add(Reference("urn:uuid:" + observation.id))
                observations.add(observation)
            }
        }
        ctxThymeleaf.clearVariables()
        ctxThymeleaf.setVariable("observations", observations)
        section.text.setDiv(getDiv("observation")).setStatus(Narrative.NarrativeStatus.GENERATED)
        return section
    }

    fun getProcedureSection(bundle: Bundle): Composition.SectionComponent {
        val section = Composition.SectionComponent()
        val procedures = ArrayList<Procedure>()
        section.code.addCoding()
            .setSystem(PatientSummary.SNOMEDCT)
            .setCode("887171000000109")
            .setDisplay("Procedues")
        section.setTitle("Procedures")
        for (entry in bundle.entry) {
            if (entry.resource is Procedure) {
                val procedure = entry.resource as Procedure
                section.entry.add(Reference("urn:uuid:" + procedure.id))
                procedures.add(procedure)
            }
        }
        ctxThymeleaf.clearVariables()
        ctxThymeleaf.setVariable("procedures", procedures)
        section.text.setDiv(getDiv("procedure")).setStatus(Narrative.NarrativeStatus.GENERATED)
        return section
    }

    fun generatePatientHtml(patient: Patient, fhirDocument: Bundle): Patient {
        if (!patient.hasText()) {
            ctxThymeleaf.clearVariables()
            ctxThymeleaf.setVariable("patient", patient)
            for (entry in fhirDocument.entry) {
                if (entry.resource is Practitioner) ctxThymeleaf.setVariable("gp", entry.resource)
                if (entry.resource is Organization) ctxThymeleaf.setVariable("practice", entry.resource)
                var practice: Practitioner
            }
            patient.text.setDiv(getDiv("patient")).setStatus(Narrative.NarrativeStatus.GENERATED)
            log.debug(patient.text.div.valueAsString)
        }
        return patient
    }

    private fun getDiv(template: String): XhtmlNode? {
        var xhtmlNode: XhtmlNode? = null
        val processedHtml = templateEngine.process(template, ctxThymeleaf)
        try {
            val parsed = xhtmlParser.parse(processedHtml, null)
            xhtmlNode = parsed.documentElement
            log.debug(processedHtml)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return xhtmlNode
    }

    companion object {
        private val log = LoggerFactory.getLogger(FhirDocUtil::class.java)
    }
}
