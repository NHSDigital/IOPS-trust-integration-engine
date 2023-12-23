package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.segment.NTE
import ca.uhn.hl7v2.model.v251.segment.ORC
import ca.uhn.hl7v2.model.v251.segment.SPM
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Annotation


class SPMtoFHIRSpecimen : Transformer<SPM, Specimen> {
    override fun transform(spm: SPM): Specimen {
        var specimen = Specimen()
        if (spm.specimenID !== null) {
            if (spm.specimenID.placerAssignedIdentifier !== null) {
                specimen.addIdentifier(
                    Identifier().setValue(spm.specimenID.placerAssignedIdentifier.entityIdentifier.value)
                )
            }
            if (spm.specimenID.fillerAssignedIdentifier !== null) {
                specimen.accessionIdentifier.setValue(spm.specimenID.fillerAssignedIdentifier.entityIdentifier.value)
            }
        }
        if (spm.specimenType !== null) {
            specimen.type.addCoding(
                Coding()
                    .setCode(spm.specimenType.identifier.value)
                    .setDisplay(spm.specimenType.text.value)
            )
        }
        if (spm.specimenCollectionDateTime !== null) {
            specimen.collection.collectedDateTimeType.value = spm.specimenCollectionDateTime.dr1_RangeStartDateTime.time.valueAsDate
        }
        return specimen
    }

}
