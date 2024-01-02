package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.datatype.CWE
import ca.uhn.hl7v2.model.v251.datatype.NM
import ca.uhn.hl7v2.model.v251.datatype.TX
import ca.uhn.hl7v2.model.v251.segment.OBX
import ca.uhn.hl7v2.model.v251.segment.ORC
import mu.KLogging
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.*
import uk.nhs.england.tie.util.FhirSystems.*


class OBXtoFHIRObservation : Transformer<OBX, Observation> {
    companion object : KLogging()
    var xcNtoFHIRReference = XCNtoFHIRReference()
    override fun transform(obx : OBX?): Observation {
        var observation = Observation()

        if (obx !== null) {
            if (obx.observationIdentifier != null && obx.observationIdentifier.identifier !== null) {
                var code =  Coding()
                    .setCode(obx.observationIdentifier.identifier.value)
                    .setDisplay(obx.observationIdentifier.text.value)

                if (obx.observationIdentifier.nameOfCodingSystem !== null) {
                    when (obx.observationIdentifier.nameOfCodingSystem.value) {
                        "SCT" -> {
                            code.system = SNOMED_CT
                        }
                        "LN" -> {
                            code.system = LOINC
                        }
                    }
                }
                observation.code.addCoding(
                    code
                )
                observation.category.add(CodeableConcept().addCoding(Coding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                    .setCode("laboratory")
                    .setDisplay("Laboratory")
                ))

                if (obx.observationIdentifier.alternateIdentifier !== null) {
                    var altCode = Coding()
                        .setCode(obx.observationIdentifier.alternateIdentifier.value)
                        .setDisplay(obx.observationIdentifier.alternateText.value)
                    if (obx.observationIdentifier.nameOfAlternateCodingSystem !== null) {
                        when (obx.observationIdentifier.nameOfAlternateCodingSystem.value) {
                            "SCT" -> {
                                altCode.system = SNOMED_CT
                            }
                            "LN" -> {
                                altCode.system = LOINC
                            }
                        }
                    }
                    observation.code.addCoding(
                        altCode
                    )
                }
            }
            if (obx.dateTimeOfTheObservation !== null ) {
                observation.effectiveDateTimeType.value = obx.dateTimeOfTheObservation.time.valueAsDate
            }
            var quantity = Quantity()
            if (obx.observationValue !== null) {
                obx.observationValue.forEach {
                    if (it.data is NM) {

                        quantity.value = (it.data as NM).value.toBigDecimal()
                    } else if (it.data is CWE) {
                        var cwe = it.data as CWE
                        var concept = CodeableConcept()
                        if (cwe.identifier !== null) {
                            var code =  Coding()
                                .setCode(cwe.identifier.value)
                                .setDisplay(cwe.text.value)

                            if (cwe.nameOfCodingSystem !== null) {
                                when (cwe.nameOfCodingSystem.value) {
                                    "SCT" -> {
                                        code.system = SNOMED_CT
                                    }
                                    "LN" -> {
                                        code.system = LOINC
                                    }
                                }
                            }
                            concept.addCoding(
                                code
                            )
                        }
                        observation.value = concept
                    }
                    else
                        if (it.data is TX) {
                        observation.setValue(StringType((it.data as TX).value))
                    }
                }

            }
            if (obx.units !== null) {
                quantity.code = (obx.units.identifier).value
                if (obx.units.nameOfCodingSystem !== null) {
                    when (obx.units.nameOfCodingSystem.value) {
                        "UCUM" -> quantity.system = UNITS_OF_MEASURE
                    }
                }
                if (obx.units.text !== null) {
                    quantity.unit = obx.units.text.value
                }
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
                               .setDisplay("High")
                           ))
                        }
                        "N"-> {
                            observation.interpretation.add(CodeableConcept().addCoding(Coding()
                                .setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
                                .setCode("N")
                                .setDisplay("Normal")
                            ))
                        }
                        "L"-> {
                            observation.interpretation.add(CodeableConcept().addCoding(Coding()
                                .setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
                                .setCode("L")
                                .setDisplay("Low")
                            ))
                        }
                    }
                }
            }
            if (obx.performingOrganizationName !== null && obx.performingOrganizationName.organizationName !== null && obx.performingOrganizationName.organizationName.value !== null) {
                observation.performer.add(Reference().setDisplay(obx.performingOrganizationName.organizationName.value))
            }
            if (obx.performingOrganizationMedicalDirector !== null) {
                val director = xcNtoFHIRReference.transform(obx.performingOrganizationMedicalDirector)
                if (director.display !== null || director.hasIdentifier()) observation.performer.add(director)
            }
        }
        return observation
    }

}
