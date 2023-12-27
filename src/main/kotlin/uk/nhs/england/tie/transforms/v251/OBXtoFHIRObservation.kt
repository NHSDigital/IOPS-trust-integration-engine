package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.datatype.NM
import ca.uhn.hl7v2.model.v251.datatype.TX
import ca.uhn.hl7v2.model.v251.segment.OBX
import ca.uhn.hl7v2.model.v251.segment.ORC
import mu.KLogging
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.*


class OBXtoFHIRObservation : Transformer<OBX, Observation> {
    companion object : KLogging()
    override fun transform(obx : OBX?): Observation {
        var observation = Observation()

        if (obx !== null) {
            if (obx.observationIdentifier != null && obx.observationIdentifier.identifier !== null) {

                observation.code.addCoding(
                    Coding()
                        .setCode(obx.observationIdentifier.identifier.value)
                        .setDisplay(obx.observationIdentifier.text.value)
                )
            }
            if (obx.dateTimeOfTheObservation !== null ) {
                observation.effectiveDateTimeType.value = obx.dateTimeOfTheObservation.time.valueAsDate
            }
            var quantity = Quantity()
            if (obx.observationValue !== null) {
                obx.observationValue.forEach {
                    if (it.data is NM) {

                        quantity.value = (it.data as NM).value.toBigDecimal()
                    } else
                        if (it.data is TX) {
                        observation.setValue(StringType((it.data as TX).value))
                    }
                }

            }
            if (obx.units !== null) {
                quantity.unit = (obx.units.identifier).value
            }
            if (quantity.value !== null) observation.setValue(quantity)
            if (obx.referencesRange !== null) {
                observation.referenceRange.add(
                    Observation.ObservationReferenceRangeComponent().setText(obx.referencesRange.value)
                )
            }
            if (obx.abnormalFlags !== null && obx.abnormalFlags.size>0) {
                obx.abnormalFlags.forEach {
                    when (it.value) {
                        "H"-> {
                           observation.interpretation.add(CodeableConcept().addCoding(Coding()
                               .setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
                               .setCode("H")
                           ))
                        }
                        "N"-> {
                            observation.interpretation.add(CodeableConcept().addCoding(Coding()
                                .setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
                                .setCode("N")
                            ))
                        }
                        "L"-> {
                            observation.interpretation.add(CodeableConcept().addCoding(Coding()
                                .setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
                                .setCode("L")
                            ))
                        }
                    }
                }
            }
        }
        return observation
    }

}
