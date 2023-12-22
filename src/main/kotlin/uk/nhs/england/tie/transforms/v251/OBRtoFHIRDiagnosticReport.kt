package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.segment.OBR
import ca.uhn.hl7v2.model.v251.segment.ORC
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.DiagnosticReport


class OBRtoFHIRDiagnosticReport : Transformer<ORC, DiagnosticReport> {
    fun transform(obr: OBR, report: DiagnosticReport? ): DiagnosticReport {
        var diagnosticReport = DiagnosticReport()


        return diagnosticReport
    }

    override fun transform(input: ORC?): DiagnosticReport {
        TODO("Not yet implemented")
    }

}
