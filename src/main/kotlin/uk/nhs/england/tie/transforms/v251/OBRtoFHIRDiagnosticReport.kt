package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.GenericPrimitive
import ca.uhn.hl7v2.model.v251.segment.OBR
import ca.uhn.hl7v2.model.v251.segment.ORC
import mu.KLogging
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.*


class OBRtoFHIRDiagnosticReport : Transformer<ORC, DiagnosticReport> {
    var XCNtoFHIRReference = XCNtoFHIRReference()
    companion object : KLogging()
    fun transform(obr: OBR, report: DiagnosticReport? ): DiagnosticReport {
        var diagnosticReport = DiagnosticReport()
        if (obr.diagnosticServSectID == null || obr.diagnosticServSectID.value == null) {
            // default to laboratory
            diagnosticReport.addCategory().addCoding(
                Coding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/v2-0074")
                    .setCode("LAB")
            )
        } else {
            diagnosticReport.addCategory().addCoding(
                Coding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/v2-0074")
                    .setCode(obr.diagnosticServSectID.value)
            )
        }
        if (obr.universalServiceIdentifier !== null ) {

            if (obr.universalServiceIdentifier.identifier !== null) {
                diagnosticReport.code.addCoding(
                    Coding()
                        .setCode(obr.universalServiceIdentifier.identifier.value)
                        .setDisplay(obr.universalServiceIdentifier.text.value)
                )
            }
        }

        if (obr.placerOrderNumber !== null ) {

            if (obr.placerOrderNumber.entityIdentifier !== null && obr.placerOrderNumber.entityIdentifier.value !== null) {
                diagnosticReport.basedOn.add(Reference()
                    .setIdentifier(Identifier()
                        .setValue(obr.placerOrderNumber.entityIdentifier.value)
                        .setType(CodeableConcept().addCoding(Coding()
                            .setCode("PLAC")
                            .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203"))))
                    .setType("ServiceRequest"))
            }
        }
        if (obr.fillerOrderNumber !== null ) {
            if (obr.fillerOrderNumber.entityIdentifier !== null && obr.fillerOrderNumber.entityIdentifier.value !== null) {
                diagnosticReport.basedOn.add(Reference()
                    .setIdentifier(Identifier()
                        .setValue(obr.fillerOrderNumber.entityIdentifier.value)
                        .setType(CodeableConcept().addCoding(Coding()
                            .setCode("FILL")
                            .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203"))))
                    .setType("ServiceRequest"))
            }
        }
        if (obr.observationDateTime !== null && obr.observationDateTime.time !==null) {
            diagnosticReport.effectiveDateTimeType.value = obr.observationDateTime.time.valueAsDate
        }
        if (obr.collectorIdentifier !== null && obr.collectorIdentifier.size >0) {
            obr.collectorIdentifier.forEach {
                diagnosticReport.performer.add(XCNtoFHIRReference.transform(it))
            }

        }
        if (obr.relevantClinicalInformation !== null) {

            if (obr.relevantClinicalInformation.extraComponents !== null) {
                var extra = obr.relevantClinicalInformation.extraComponents
                if (extra.numComponents() > 0)  {
                    // In HL7 examples this is 2.7.1 CWE field, just processing the text element at present
                    var varies = extra.getComponent(0)
                    if (varies.data is GenericPrimitive) {
                        var st = varies.data as GenericPrimitive
                        if (st.value !== null && !st.value.isEmpty()) {
                            var note = Extension("http://hl7.org/fhir/5.0/StructureDefinition/extension-DiagnosticReport.note")
                            note.setValue(Annotation().setText(st.value))
                            diagnosticReport.extension.add(note)
                        }
                    }
                }
                logger.info(extra.toString())
            } else {
                var note = Extension("http://hl7.org/fhir/5.0/StructureDefinition/extension-DiagnosticReport.note")
                note.setValue(Annotation().setText(obr.relevantClinicalInformation.value))

                diagnosticReport.extension.add(note)
            }
        }
        if (obr.technician.isNotEmpty()) {
            obr.technician.forEach {
                logger.info(it.toString())
            }
        }
        if (obr.principalResultInterpreter !== null) {
                logger.info(obr.principalResultInterpreter.toString())

        }
        if (obr.assistantResultInterpreter.isNotEmpty()) {
            obr.assistantResultInterpreter.forEach {
                logger.info(it.toString())
            }


        }


        return diagnosticReport
    }

    override fun transform(input: ORC?): DiagnosticReport {
        TODO("Not yet implemented")
    }

}
