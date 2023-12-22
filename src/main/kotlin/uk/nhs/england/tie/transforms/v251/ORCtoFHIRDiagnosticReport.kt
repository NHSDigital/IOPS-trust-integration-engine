package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.segment.ORC
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.DiagnosticReport


class ORCtoFHIRDiagnosticReport : Transformer<ORC, DiagnosticReport> {
    override fun transform(orc: ORC?): DiagnosticReport {
        var diagnosticReport = DiagnosticReport()

        return diagnosticReport
    }

}
