package uk.nhs.england.tie.component

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.model.api.Include
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.thymeleaf.TemplateEngine
import org.w3c.dom.Document
import org.xhtmlrenderer.pdf.ITextRenderer
import org.xhtmlrenderer.resource.FSEntityResolver
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource


class PatientSummary(val client: IGenericClient, @Qualifier("R4") val ctxFHIR : FhirContext, val templateEngine: TemplateEngine) {

    private val contextClassLoader: ClassLoader
        private get() = Thread.currentThread().getContextClassLoader()


    var df: DateFormat = SimpleDateFormat("HHmm_dd_MM_yyyy")
    var composition: Composition? = null
    private var fhirBundleUtil: FhirBundleUtil = FhirBundleUtil(Bundle.BundleType.DOCUMENT)

    /*
    @Throws(Exception::class)
    override fun run(vararg args: String) {
        if (args.size > 0 && args[0] == "exitcode") {
            throw Exception()
        }
        client = ctxFHIR.newRestfulGenericClient("https://data.developer.nhs.uk/ccri-fhir/STU3/")
        client.setEncoding(EncodingEnum.XML)
        outputCareRecord("1098")
        //  outputCareRecord("1177");
        val encounterBundle = buildEncounterDocument(client, IdType().setValue("1700"))
        val date = Date()
        val xmlResult = ctxFHIR.newXmlParser().setPrettyPrint(true).encodeResourceToString(encounterBundle)
        Files.write(
            Paths.get("/Temp/" + df.format(date) + "+encounter-" + "1700" + "-document.xml"),
            xmlResult.toByteArray()
        )
    }*/

    @Throws(Exception::class)
    fun outputCareRecord(patientId: String) {
        val date = Date()
        val careRecord = getCareRecord(patientId)
        val xmlResult = this.ctxFHIR.newXmlParser().setPrettyPrint(true).encodeResourceToString(careRecord)
        Files.write(
            Paths.get("/Temp/" + df.format(date) + "+patientCareRecord-" + patientId + ".xml"),
            xmlResult.toByteArray()
        )
        Files.write(
            Paths.get("/Temp/" + df.format(date) + "+patientCareRecord-" + patientId + ".json"),
            ctxFHIR.newJsonParser().setPrettyPrint(true).encodeResourceToString(careRecord).toByteArray()
        )
        /*
        String htmlFilename = "/Temp/"+df.format(date)+"+patient-"+patientId+".html";
        performTransform(xmlResult,htmlFilename,"XML/DocumentToHTML.xslt");
        outputPDF(htmlFilename, "/Temp/"+df.format(date)+"+patient-"+patientId+".pdf");

        IGenericClient clientTest = ctxFHIR.newRestfulGenericClient("http://127.0.0.1:8080/careconnect-gateway/STU3/");
        clientTest.create().resource(careRecord).execute();
        */
    }

    @Throws(Exception::class)
    public fun convertPDF(processedHtml: String) : ByteArrayOutputStream? {
        var os: ByteArrayOutputStream? = null
        val fileName = UUID.randomUUID().toString()
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

    @Throws(Exception::class)
    fun buildEncounterDocument(client: IGenericClient, encounterId: IdType): Bundle {
        fhirBundleUtil = FhirBundleUtil(Bundle.BundleType.DOCUMENT)
        val compositionBundle = Bundle()

        // Main resource of a FHIR Bundle is a Composition
        composition = Composition()
        composition!!.setId(UUID.randomUUID().toString())
        compositionBundle.addEntry().setResource(composition)

        // composition.getMeta().addProfile(CareConnectProfile.Composition_1);
        composition!!.setTitle("Encounter Document")
        composition!!.setDate(Date())
        composition!!.setStatus(Composition.CompositionStatus.FINAL)
        val leedsTH = getOrganization(client, "RR8")
        compositionBundle.addEntry().setResource(leedsTH)
        composition!!.addAttester()
            .setParty(Reference("Organization/" + leedsTH!!.idElement.idPart))
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
        composition!!.type.addCoding()
            .setCode("371531000")
            .setDisplay("Report of clinical encounter")
            .setSystem(SNOMEDCT)
        fhirBundleUtil.processBundleResources(compositionBundle)
        fhirBundleUtil.processReferences()
        val encounterBundle = getEncounterBundleRev(client, encounterId.idPart)
        var encounter: Encounter? = null
        for (entry in encounterBundle.entry) {
            val resource = entry.resource
            if (encounter == null && entry.resource is Encounter) {
                encounter = entry.resource as Encounter
            }
        }
        var patientId: String? = null
        if (encounter != null) {
            patientId = encounter.subject.referenceElement.idPart
            log.debug(encounter.subject.referenceElement.idPart)


            // This is a synthea patient
            val patientBundle = getPatientBundle(client, patientId)
            fhirBundleUtil.processBundleResources(patientBundle)
            if (fhirBundleUtil.patient == null) throw Exception("404 Patient not found")
            composition!!.setSubject(Reference("Patient/$patientId"))
        }
        if (fhirBundleUtil.patient == null) throw UnprocessableEntityException()
        fhirBundleUtil.processBundleResources(encounterBundle)
        fhirBundleUtil.processReferences()
        val fhirDoc = FhirDocUtil(templateEngine)
        composition!!.addSection(fhirDoc.getEncounterSection(fhirBundleUtil.fhirDocument))
        var section: Composition.SectionComponent = fhirDoc.getConditionSection(fhirBundleUtil.fhirDocument)
        if (section.entry.size > 0) composition!!.addSection(section)
        section = fhirDoc.getMedicationStatementSection(fhirBundleUtil.fhirDocument)
        if (section.entry.size > 0) composition!!.addSection(section)
        section = fhirDoc.getMedicationRequestSection(fhirBundleUtil.fhirDocument)
        if (section.entry.size > 0) composition!!.addSection(section)
        section = fhirDoc.getAllergySection(fhirBundleUtil.fhirDocument)
        if (section.entry.size > 0) composition!!.addSection(section)
        section = fhirDoc.getObservationSection(fhirBundleUtil.fhirDocument)
        if (section.entry.size > 0) composition!!.addSection(section)
        section = fhirDoc.getProcedureSection(fhirBundleUtil.fhirDocument)
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
        composition!!.setTitle("Patient Summary Care Record")
        composition!!.setDate(Date())
        composition!!.setStatus(Composition.CompositionStatus.FINAL)
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
        val patientBundle = getPatientBundle(client, patientId)
        fhirBundleUtil.processBundleResources(patientBundle)
        if (fhirBundleUtil.patient == null) throw Exception("404 Patient not found")
        composition!!.setSubject(Reference("Patient/$patientId"))
        val fhirDoc = FhirDocUtil(templateEngine)

        fhirDoc.generatePatientHtml(fhirBundleUtil.patient, patientBundle)

        /* CONDITION */
        val conditionBundle = getConditionBundle(patientId)
        fhirBundleUtil.processBundleResources(conditionBundle)
        composition!!.addSection(fhirDoc.getConditionSection(conditionBundle))

        /* MEDICATION STATEMENT */
        val medicationStatementBundle = getMedicationStatementBundle(patientId)
        fhirBundleUtil.processBundleResources(medicationStatementBundle)
        composition!!.addSection(fhirDoc.getMedicationStatementSection(medicationStatementBundle))

        val medicationRequestBundle = getMedicationRequestBundle(patientId)
        val section = fhirDoc.getMedicationRequestSection(medicationRequestBundle)
        if (section.entry.size > 0) composition!!.addSection(section)

        /* ALLERGY INTOLERANCE */
        val allergyBundle = getAllergyBundle(patientId)
        fhirBundleUtil.processBundleResources(allergyBundle)
        composition!!.addSection(fhirDoc.getAllergySection(allergyBundle))

        /* ENCOUNTER */
        val encounterBundle = getEncounterBundle(patientId)
        fhirBundleUtil.processBundleResources(encounterBundle)
        composition!!.addSection(fhirDoc.getEncounterSection(encounterBundle))
        fhirBundleUtil.processReferences()
        log.debug(ctxFHIR.newJsonParser().setPrettyPrint(true).encodeResourceToString(fhirBundleUtil.fhirDocument))
        return fhirBundleUtil.fhirDocument
    }

    private fun getPatientBundle(client: IGenericClient?, patientId: String?): Bundle {
        if (client != null) {
            return client
                .search<IBaseBundle>()
                .forResource(Patient::class.java)
                .where(Patient.RES_ID.exactly().code(patientId))
                .include(Patient.INCLUDE_GENERAL_PRACTITIONER)
                .include(Patient.INCLUDE_ORGANIZATION)
                .returnBundle(Bundle::class.java)
                .execute()
        } else
        return Bundle()
    }

    private fun getEncounterBundleRev(
        client: IGenericClient?,
        encounterId: String
    ): Bundle {
        if (client != null) {
            return client
                .search<IBaseBundle>()
                .forResource(Encounter::class.java)
                .where(Patient.RES_ID.exactly().code(encounterId))
                .revInclude(Include("*"))
                .include(Include("*"))
                .count(100) // be careful of this TODO
                .returnBundle(Bundle::class.java)
                .execute()
        } else {
            return Bundle()
        }
    }

    private fun getConditionBundle(patientId: String): Bundle {
        return client
            .search<IBaseBundle>()
            .forResource(Condition::class.java)
            .where(Condition.PATIENT.hasId(patientId))
          //  .and(Condition.CLINICAL_STATUS.exactly().code("active"))
            .returnBundle(Bundle::class.java)
            .execute()
    }

    private fun getEncounterBundle(patientId: String): Bundle {
        return client
            .search<IBaseBundle>()
            .forResource(Encounter::class.java)
            .where(Encounter.PATIENT.hasId(patientId))
            .count(3) // Last 3 entries same as GP Connect
            .returnBundle(Bundle::class.java)
            .execute()
    }

    private fun getOrganization(client: IGenericClient, sdsCode: String): Organization? {
        var organization: Organization? = null
        val bundle = client
            .search<IBaseBundle>()
            .forResource(Organization::class.java)
            .where(Organization.IDENTIFIER.exactly().code(sdsCode))
            .returnBundle(Bundle::class.java)
            .execute()
        if (bundle.entry.size > 0) {
            if (bundle.entry[0].resource is Organization) organization = bundle.entry[0].resource as Organization
        }
        return organization
    }

    private fun getMedicationStatementBundle(patientId: String): Bundle {
        return client
            .search<IBaseBundle>()
            .forResource(MedicationStatement::class.java)
            .where(MedicationStatement.PATIENT.hasId(patientId))
            .and(MedicationStatement.STATUS.exactly().code("active"))
            .returnBundle(Bundle::class.java)
            .execute()
    }

    private fun getMedicationRequestBundle(patientId: String): Bundle {
        return client
            .search<IBaseBundle>()
            .forResource(MedicationRequest::class.java)
            .where(MedicationRequest.PATIENT.hasId(patientId))
            .and(MedicationRequest.STATUS.exactly().code("active"))
            .returnBundle(Bundle::class.java)
            .execute()
    }

    private fun getAllergyBundle(patientId: String): Bundle {
        return client
            .search<IBaseBundle>()
            .forResource(AllergyIntolerance::class.java)
            .where(AllergyIntolerance.PATIENT.hasId(patientId))
            .returnBundle(Bundle::class.java)
            .execute()
    }

    fun convertHTML(xmlInput: String, styleSheet: String) : String? {

        // Input xml data file
        val classLoader = contextClassLoader

        // Input xsl (stylesheet) file
        val xslInput = classLoader.getResource(styleSheet).file

        // Set the property to use xalan processor
        System.setProperty(
            "javax.xml.transform.TransformerFactory",
            "org.apache.xalan.processor.TransformerFactoryImpl"
        )

        // try with resources
        try {
            val xml: InputStream = ByteArrayInputStream(xmlInput.toByteArray(StandardCharsets.UTF_8))
            val os = ByteArrayOutputStream()
            val xsl = FileInputStream(xslInput)

            // Instantiate a transformer factory
            val tFactory = TransformerFactory.newInstance()

            // Use the TransformerFactory to process the stylesheet source and produce a Transformer
            val styleSource = StreamSource(xsl)
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
        private val log = LoggerFactory.getLogger(PatientSummary::class.java)
        const val SNOMEDCT = "http://snomed.info/sct"
    }
}
