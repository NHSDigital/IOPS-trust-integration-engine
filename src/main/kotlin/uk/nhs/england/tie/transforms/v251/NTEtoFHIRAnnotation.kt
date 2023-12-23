package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.segment.NTE
import ca.uhn.hl7v2.model.v251.segment.ORC
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.Annotation
import org.hl7.fhir.r4.model.DiagnosticReport


class NTEtoFHIRAnnotation : Transformer<NTE, Annotation> {
    override fun transform(nte: NTE): Annotation {
        var annotation = Annotation()
        annotation.text = ""
        if (nte.comment !== null) {
            nte.comment.forEach {
                annotation.text += it.value + "/r"
            }
        }
        return annotation
    }

}
