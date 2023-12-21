package uk.nhs.england.tie.component

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.model.api.Include
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.utilities.xhtml.XhtmlNode
import org.hl7.fhir.utilities.xhtml.XhtmlParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.w3c.dom.Document
import org.xhtmlrenderer.pdf.ITextRenderer
import org.xhtmlrenderer.resource.FSEntityResolver
import uk.nhs.england.tie.util.FhirSystems
import java.io.*
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource


class FHIRDocument(val client: IGenericClient, @Qualifier("R4") val ctxFHIR : FhirContext, val templateEngine: TemplateEngine) {

    private val contextClassLoader: ClassLoader
        private get() = Thread.currentThread().getContextClassLoader()
    var ctxThymeleaf = Context()
    private val xhtmlParser = XhtmlParser()


    var df: DateFormat = SimpleDateFormat("HHmm_dd_MM_yyyy")
    var composition: Composition? = null
    private var fhirBundleUtil: FhirBundleUtil = FhirBundleUtil(Bundle.BundleType.DOCUMENT)




    fun getDiagnosticReport(reportId: String): Bundle {
        fhirBundleUtil = FhirBundleUtil(Bundle.BundleType.DOCUMENT)
        // Main resource of a FHIR Bundle is a Composition
        val compositionBundle = Bundle()
        composition = Composition()
        composition!!.setId(UUID.randomUUID().toString())
        compositionBundle.addEntry().setResource(composition)
        composition!!.setTitle("Laboratory Report")
        composition!!.setDate(Date())
        composition!!.setStatus(Composition.CompositionStatus.FINAL)
        composition!!.type = CodeableConcept().addCoding(Coding()
            .setSystem(FhirSystems.LOINC)
            .setCode("11502-2"))

        val leedsTH = getOrganization(client, "RR8")
        compositionBundle.addEntry().setResource(leedsTH)
        composition!!.addAttester()
            .setParty(Reference(leedsTH!!.id))
            .setMode(Composition.CompositionAttestationMode.OFFICIAL)
        val device = Device()
        device.setId(UUID.randomUUID().toString())
        device.type.addCoding()
            .setSystem("http://snomed.info/sct")
            .setCode("58153004")
            .setDisplay("Android")
        device.setOwner(Reference("Organization/" + leedsTH.idElement.idPart))
        compositionBundle.addEntry().setResource(device)
        composition!!.addAuthor(Reference("Device/" + device.idElement.idPart))
        fhirBundleUtil.processBundleResources(compositionBundle)
        fhirBundleUtil.processReferences()
        val reportBundle = getDiagnosticReportBundleRev(reportId)
        var diagnosticReport :DiagnosticReport? = null
        for (entry in reportBundle.entry) {
            if (diagnosticReport == null && entry.resource is DiagnosticReport) {
                diagnosticReport = entry.resource as DiagnosticReport
            }
        }
        var patientId: String? = null
        if (diagnosticReport != null) {
            patientId = diagnosticReport.subject.referenceElement.idPart
            log.debug(diagnosticReport.subject.referenceElement.idPart)

            fhirBundleUtil.processBundleResources(reportBundle)
            if (fhirBundleUtil.patient == null) throw Exception("404 Patient not found")
            composition!!.setSubject(Reference("Patient/$patientId"))
        }
        if (fhirBundleUtil.patient == null) throw UnprocessableEntityException()

        fhirBundleUtil.processReferences()

        generatePatientHtml(fhirBundleUtil.patient!!, reportBundle)

        /*
        var section: Composition.SectionComponent
        for (entry in reportBundle.getEntry()) {
            if (entry.resource is Observation) {
                val observation = entry.resource as Observation

                if (observation.hasMember.size>0) {
                    //var obs = getObservationBundleRev(observation.idPart)
                    //if (obs !== null) fhirBundleUtil.processBundleResources(obs)
                    observation.hasMember.forEach({
                        log.info(it.reference)
                        var obs = getObservation(it.reference.replace("Observation/",""))
                        log.info(obs.entry.size.toString())
                        if (obs.entry.size>0) fhirBundleUtil.processBundleResources(obs)
                    })
                }
            }
        }
        section = getObservationSection(fhirBundleUtil.fhirDocument)
        if (section.entry.size > 0) composition!!.addSection(section)

         */
        var section = getDiagnosticReportSection(fhirBundleUtil.fhirDocument)
        if (section.entry.size > 0) composition!!.addSection(section)
        return fhirBundleUtil.fhirDocument
    }

    @Throws(Exception::class)
    fun getCareRecord(patientId: String): Bundle {
        // Create Bundle of type Document
        var patientId = patientId
        fhirBundleUtil = FhirBundleUtil(Bundle.BundleType.DOCUMENT)
        // Main resource of a FHIR Bundle is a Composition
        val compositionBundle = Bundle()
        composition = Composition()
        composition!!.setId(UUID.randomUUID().toString())
        compositionBundle.addEntry().setResource(composition)
        composition!!.setTitle("International Patient Summary")
        composition!!.setDate(Date())
        composition!!.setStatus(Composition.CompositionStatus.FINAL)
        composition!!.type = CodeableConcept().addCoding(Coding()
            .setSystem(FhirSystems.LOINC)
            .setCode("60591-5"))

        val leedsTH = getOrganization(client, "RR8")
        compositionBundle.addEntry().setResource(leedsTH)
        composition!!.addAttester()
            .setParty(Reference(leedsTH!!.id))
            .setMode(Composition.CompositionAttestationMode.OFFICIAL)
        val device = Device()
        device.setId(UUID.randomUUID().toString())
        device.type.addCoding()
            .setSystem("http://snomed.info/sct")
            .setCode("58153004")
            .setDisplay("Android")
        device.setOwner(Reference("Organization/" + leedsTH.idElement.idPart))
        compositionBundle.addEntry().setResource(device)
        composition!!.addAuthor(Reference("Device/" + device.idElement.idPart))
        fhirBundleUtil.processBundleResources(compositionBundle)
        fhirBundleUtil.processReferences()

        // This is a synthea patient
        val patientBundle = getPatientBundle(patientId)
        fhirBundleUtil.processBundleResources(patientBundle)
        if (fhirBundleUtil.patient == null) throw Exception("404 Patient not found")
        composition!!.setSubject(Reference("Patient/$patientId"))

        generatePatientHtml(fhirBundleUtil.patient!!, patientBundle)

        /* CONDITION */
        val conditionBundle = getConditionBundle(patientId)
        fhirBundleUtil.processBundleResources(conditionBundle)
        composition!!.addSection(getConditionSection(conditionBundle))

        /* MEDICATION STATEMENT AND REQUEST */
        val medicationStatementBundle = getMedicationStatementBundle(patientId)
        fhirBundleUtil.processBundleResources(medicationStatementBundle)
        val medicationRequestBundle = getMedicationRequestBundle(patientId)
        fhirBundleUtil.processBundleResources(medicationRequestBundle)
        val section = getMedicationsSection(medicationRequestBundle, medicationStatementBundle)
        if (section.entry.size > 0) composition!!.addSection(section)

        /* ALLERGY INTOLERANCE */
        val allergyBundle = getAllergyBundle(patientId)
        fhirBundleUtil.processBundleResources(allergyBundle)
        composition!!.addSection(getAllergySection(allergyBundle))

        /* DIAGNOSTIC REPORT */
        val reportBundle = getDiagnosticReportBundle(patientId)
        fhirBundleUtil.processBundleResources(reportBundle)
        composition!!.addSection(getDiagnosticReportSection(reportBundle))

        /* OBSERVATION */
        val observationBundle = getObservationBundle(patientId)
        fhirBundleUtil.processBundleResources(observationBundle)
        composition!!.addSection(getObservationSection(observationBundle))


        /* ENCOUNTER */
        val encounterBundle = getEncounterBundle(patientId)
        fhirBundleUtil.processBundleResources(encounterBundle)
        composition!!.addSection(getEncounterSection(encounterBundle))
        fhirBundleUtil.processReferences()

        return fhirBundleUtil.fhirDocument
    }

    private fun getPatientBundle(patientId: String?): Bundle {
        var bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle = client
                .search<IBaseBundle>()
                .forResource(Patient::class.java)
                .where(Patient.RES_ID.exactly().code(patientId))
                .include(Patient.INCLUDE_GENERAL_PRACTITIONER)
                .include(Patient.INCLUDE_ORGANIZATION)
                .returnBundle(Bundle::class.java)
                .execute()
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return bundle
    }

    private fun getEncounterBundleRev(
        encounterId: String
    ): Bundle {
        var bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle = client
                .search<IBaseBundle>()
                .forResource(Encounter::class.java)
                .where(Encounter.RES_ID.exactly().code(encounterId))
                .revInclude(Include("*"))
                .include(Include("*"))
                .count(100) // be careful of this TODO
                .returnBundle(Bundle::class.java)
                .execute()
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return bundle
    }

    private fun getDiagnosticReportBundleRev(
        reportId: String
    ): Bundle {
        var bundle: Bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle = client
                .search<IBaseBundle>()
                .forResource(DiagnosticReport::class.java)
                .where(DiagnosticReport.RES_ID.exactly().code(reportId))
                .revInclude(Include("*"))
                .include(Include("*"))
                .count(100) // be careful of this TODO
                .returnBundle(Bundle::class.java)
                .execute()
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return bundle
    }

    private fun getDiagnosticReportBundle(
        patientId: String
    ): Bundle {
        var bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle = client
                .search<IBaseBundle>()
                .forResource(DiagnosticReport::class.java)
                .where(DiagnosticReport.PATIENT.hasId(patientId))
                .sort().descending("date")
                .count(5) // be careful of this TODO
                .returnBundle(Bundle::class.java)
                .execute()
        } catch (ex: Exception) {
            // do nothing
            log.error(ex.message)
            retry--
            if (retry == 0) throw ex
        }
    }
    return bundle
    }
    private fun getObservationBundle(
        patientId: String
    ): Bundle {
        var bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle =   client
                .search<IBaseBundle>()
                .forResource(Observation::class.java)
                .where(Observation.PATIENT.hasId(patientId))
                .sort().descending("date")
                .count(100) // be careful of this TODO
                .returnBundle(Bundle::class.java)
                .execute()
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return bundle
    }
    private fun getObservation(
        reportId: String
    ): Bundle {
        var bundle: Bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle =  client
                .search<IBaseBundle>()
                .forResource(Observation::class.java)
                .where(Observation.RES_ID.exactly().code(reportId))
                .count(100) // be careful of this TODO
                .returnBundle(Bundle::class.java)
                .execute()
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return bundle
    }


    private fun getConditionBundle(patientId: String): Bundle {

        var bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle = client
            .search<IBaseBundle>()
            .forResource(Condition::class.java)
            .where(Condition.PATIENT.hasId(patientId))
          //  .and(Condition.CLINICAL_STATUS.exactly().code("active"))
            .returnBundle(Bundle::class.java)
            .execute()
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return bundle
    }

    private fun getEncounterBundle(patientId: String): Bundle {

        var bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle = client
                    .search<IBaseBundle>()
                    .forResource(Encounter::class.java)
                    .where(Encounter.PATIENT.hasId(patientId))
                    .count(3) // Last 3 entries same as GP Connect
                    .returnBundle(Bundle::class.java)
                    .execute()
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return bundle
    }

    private fun getOrganization(client: IGenericClient, sdsCode: String): Organization? {
        var organization: Organization? = null
        var bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle = client
            .search<IBaseBundle>()
            .forResource(Organization::class.java)
            .where(Organization.IDENTIFIER.exactly().code(sdsCode))
            .returnBundle(Bundle::class.java)
            .execute()
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        if (bundle.entry.size > 0) {
            if (bundle.entry[0].resource is Organization) organization = bundle.entry[0].resource as Organization
        }
        return organization
    }

    private fun getMedicationStatementBundle(patientId: String): Bundle {
        var bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle = client
            .search<IBaseBundle>()
            .forResource(MedicationStatement::class.java)
            .where(MedicationStatement.PATIENT.hasId(patientId))
            .and(MedicationStatement.STATUS.exactly().code("active"))
            .returnBundle(Bundle::class.java)
            .execute()
        } catch (ex: Exception) {
            // do nothing
            log.error(ex.message)
            retry--
            if (retry == 0) throw ex
        }
    }
return bundle
    }

    private fun getMedicationRequestBundle(patientId: String): Bundle {
        var bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle = client
            .search<IBaseBundle>()
            .forResource(MedicationRequest::class.java)
            .where(MedicationRequest.PATIENT.hasId(patientId))
            .and(MedicationRequest.STATUS.exactly().code("active"))
            .sort().descending("date")
            .returnBundle(Bundle::class.java)
            .execute()
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return bundle
    }

    private fun getAllergyBundle(patientId: String): Bundle {
        var bundle = Bundle()
        var retry = 3
        while (retry > 0) {
            try {
                bundle = client
                    .search<IBaseBundle>()
                    .forResource(AllergyIntolerance::class.java)
                    .where(AllergyIntolerance.PATIENT.hasId(patientId))
                    .returnBundle(Bundle::class.java)
                    .execute()
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return bundle
    }

    @Throws(Exception::class)
    fun convertPDF(processedHtml: String) : ByteArrayOutputStream? {
        var os: ByteArrayOutputStream? = null

        try {

            os = ByteArrayOutputStream()

            val documentBuilderFactory = DocumentBuilderFactory.newInstance()
            documentBuilderFactory.isValidating = false
            val builder: DocumentBuilder = documentBuilderFactory.newDocumentBuilder()
            builder.setEntityResolver(FSEntityResolver.instance())
            val document: Document = builder.parse(ByteArrayInputStream(processedHtml.toByteArray()))

            val renderer = ITextRenderer()
            renderer.setDocument(document, null)
            renderer.layout()
            renderer.createPDF(os, false)
            renderer.finishPDF()
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (e: IOException) { /*ignore*/
                }
            }
        }
        return os
    }


    fun convertHTML(xmlInput: String, styleSheet: String) : String? {

        // Input xml data file
        val classLoader = javaClass.classLoader
        val xslInput = classLoader.getResourceAsStream(styleSheet)

        // Set the property to use xalan processor
        System.setProperty(
            "javax.xml.transform.TransformerFactory",
            "org.apache.xalan.processor.TransformerFactoryImpl"
        )

        // try with resources
        try {
            val xml: InputStream = ByteArrayInputStream(xmlInput.toByteArray(StandardCharsets.UTF_8))
            val os = ByteArrayOutputStream()


            // Instantiate a transformer factory
            val tFactory = TransformerFactory.newInstance()

            // Use the TransformerFactory to process the stylesheet source and produce a Transformer
            val styleSource = StreamSource(xslInput)
            val transformer = tFactory.newTransformer(styleSource)

            // Use the transformer and perform the transformation
            val xmlSource = StreamSource(xml)
            val result = StreamResult(os)
            transformer.transform(xmlSource, result)
            return os.toString()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }



    companion object {
        private val log = LoggerFactory.getLogger(FHIRDocument::class.java)
    }
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
                Coding().setSystem(FhirSystems.SNOMED_CT)
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
        section.code
            .addCoding(Coding()
                .setSystem(FhirSystems.LOINC)
                .setCode("8716-3"))
            .addCoding(Coding()
                .setSystem(FhirSystems.SNOMED_CT)
                .setCode("425044008")
                .setDisplay("Physical exam section"))

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
    fun getDiagnosticReportSection(bundle: Bundle): Composition.SectionComponent {
        val section = Composition.SectionComponent()
        val reports = ArrayList<DiagnosticReport>()
        section.code
            .addCoding(Coding()
                .setSystem(FhirSystems.LOINC)
                .setCode("30954-2"))

        section.setTitle("Investigation Results")
        for (entry in bundle.entry) {
            if (entry.resource is DiagnosticReport) {
                val report = entry.resource as DiagnosticReport
                section.entry.add(Reference("urn:uuid:" + report.id))
                reports.add(report)
            }
        }
        var html : XhtmlNode? = null
        reports.forEach {
            ctxThymeleaf.clearVariables()
            ctxThymeleaf.setVariable("reports", mutableListOf(it))
            val node = getDiv("diagnosticReport")
            if (node!==null) {
                if (html === null) html = node
                else {
                    node.childNodes.forEach {
                        html!!.childNodes.add(it)
                    }
                }

            }
            val observations = Bundle()
            if (it.hasResult()) {
                it.result.forEach{
                    val observation = fhirBundleUtil.getResource(it)
                    if (observation == null) {
                        val obs = getObservation(it.reference.replace("Observation/",""))

                        if (obs.hasEntry() && obs.entryFirstRep.resource is Observation) {
                            val referenceObservation = obs.entryFirstRep.resource as Observation
                            observations.addEntry().setResource(referenceObservation)
                            if (referenceObservation.hasMember.size>0) {
                                referenceObservation.hasMember.forEach{
                                    val observation2 = fhirBundleUtil.getResource(it)
                                    if (observation2 == null) {
                                        val obs2 = getObservation(it.reference.replace("Observation/", ""))
                                        if (obs2.hasEntry() && obs2.entryFirstRep.resource is Observation) {
                                            observations.addEntry().setResource(obs2.entryFirstRep.resource)
                                        }
                                    }
                                }
                            } else {
                                // Not a observation group
                            }
                        }
                    }
                }
            }
            if (observations.hasEntry()) {
                fhirBundleUtil.processBundleResources(observations)
                ctxThymeleaf.clearVariables()
                val subobs = ArrayList<Observation>()
                observations.entry.forEach{
                    if (it.hasResource() && it.resource is Observation) subobs.add(it.resource as Observation)
                }
                ctxThymeleaf.setVariable("observations", subobs)
                val node = getDiv("observation")
                if (node!==null) {
                    if (html === null) html = node
                    else {
                        node.childNodes.forEach {
                            html!!.childNodes.add(it)
                        }
                    }
                }
            }
        }

        section.text.setDiv(html).setStatus(Narrative.NarrativeStatus.GENERATED)
        return section
    }

    fun getProcedureSection(bundle: Bundle): Composition.SectionComponent {
        val section = Composition.SectionComponent()
        val procedures = ArrayList<Procedure>()
        section.code.addCoding()
            .setSystem(FhirSystems.SNOMED_CT)
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
}
