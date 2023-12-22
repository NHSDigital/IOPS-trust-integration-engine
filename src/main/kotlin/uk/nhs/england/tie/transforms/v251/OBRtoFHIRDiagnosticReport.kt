package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.segment.OBR
import ca.uhn.hl7v2.model.v251.segment.ORC
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.*


class OBRtoFHIRDiagnosticReport : Transformer<ORC, DiagnosticReport> {
    fun transform(obr: OBR, report: DiagnosticReport? ): DiagnosticReport {
        var diagnosticReport = DiagnosticReport()

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
                    .setIdentifier(Identifier().setValue(obr.placerOrderNumber.entityIdentifier.value))
                    .setType("ServiceRequest"))
            }
        }
        if (obr.fillerOrderNumber !== null ) {
            if (obr.fillerOrderNumber.entityIdentifier !== null && obr.fillerOrderNumber.entityIdentifier.value !== null) {
                diagnosticReport.basedOn.add(Reference()
                    .setIdentifier(Identifier().setValue(obr.fillerOrderNumber.entityIdentifier.value))
                    .setType("ServiceRequest"))
            }
        }
        if (obr.observationDateTime !== null && obr.observationDateTime.time !==null) {
            diagnosticReport.effectiveDateTimeType.value = obr.observationDateTime.time.valueAsDate
        }

        return diagnosticReport
    }

    override fun transform(input: ORC?): DiagnosticReport {
        TODO("Not yet implemented")
    }

}
