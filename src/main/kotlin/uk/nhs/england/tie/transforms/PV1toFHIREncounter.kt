package uk.nhs.england.tie.transforms

import ca.uhn.hl7v2.model.v24.segment.PV1
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.*
import uk.nhs.england.tie.util.FhirSystems

class PV1toFHIREncounter : Transformer<PV1, Encounter> {

    var xcNtoReference: XCNtoFHIRReference = XCNtoFHIRReference()

    override fun transform(pv1: PV1): Encounter {
        var encounter = Encounter()
        var odsCode :String? = null
        if (pv1.assignedPatientLocation != null ) {
            if (!pv1.assignedPatientLocation.pointOfCare.isEmpty) {
                encounter.setServiceProvider(
                    Reference().setIdentifier(Identifier().setSystem(FhirSystems.ODS_CODE).setValue(pv1.assignedPatientLocation.pointOfCare.value))
                )
                odsCode = pv1.assignedPatientLocation.pointOfCare.value
            }
            if (!pv1.assignedPatientLocation.facility.isEmpty) {
                val location =Encounter.EncounterLocationComponent().setLocation(
                    Reference().setIdentifier(Identifier()
                        .setSystem(FhirSystems.ODS_SITE_CODE)
                        .setValue(pv1.assignedPatientLocation.facility.namespaceID.value)))
                if (pv1.admitDateTime != null) {
                    try {
                        location.period.start = pv1.admitDateTime.timeOfAnEvent.valueAsDate
                    } catch (ex: Exception) {}
                }
                if (pv1.dischargeDateTime != null) {
                    for (ts in pv1.dischargeDateTime) {

                        try {
                            location.period.end = ts.timeOfAnEvent.valueAsDate
                        } catch (ex: Exception) {
                        }
                    }
                }
                encounter.addLocation(location)
            }
        }
        if (pv1.visitNumber != null) {
            if (odsCode == null) {
                encounter.addIdentifier().setValue(pv1.visitNumber.id.value).system =
                    "http://terminology.hl7.org/CodeSystem/v2-0203"
            } else {
                encounter.addIdentifier().setValue(pv1.visitNumber.id.value).system =
                    "https://fhir.nhs.uk/" + odsCode + "/Id/Encounter"
            }
        }
        if (pv1.alternateVisitID != null) {
            encounter.addIdentifier().value = pv1.alternateVisitID.id.value
        }
        if (pv1.patientClass.value != null) {
            when (pv1.patientClass.value) {
                "E" -> {
                    encounter.class_ = Coding().setCode("EMER").setDisplay("emergency")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                }

                "O" -> {
                    encounter.class_ = Coding().setCode("AMB").setDisplay("ambulatory")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")

                }

                "I" -> {
                    encounter.class_ = Coding().setCode("IMP").setDisplay("inpatient encounter")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")

                }

                "P" -> {
                    encounter.class_ = Coding().setCode("PRENC").setDisplay("pre-admission")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                    encounter.status = Encounter.EncounterStatus.PLANNED
                }
            }
        }
        if (pv1.admissionType != null) {
            var admissionMethod = Extension().setUrl("https://fhir.hl7.org.uk/StructureDefinition/Extension-UKCore-AdmissionMethod")
                .setValue(CodeableConcept().addCoding(
                    Coding().setSystem("https://fhir.hl7.org.uk/CodeSystem/UKCore-AdmissionMethodEngland").setCode(pv1.admissionType.value)

                ))
            encounter.addExtension(admissionMethod)
        }

        if (pv1.admitDateTime != null) {
            try {
                encounter.period.start = pv1.admitDateTime.timeOfAnEvent.valueAsDate
                encounter.status = Encounter.EncounterStatus.INPROGRESS
            } catch (ex: Exception) {
            }
        }
        if (pv1.dischargeDateTime != null) {
            for (ts in pv1.dischargeDateTime) {
                encounter.status = Encounter.EncounterStatus.FINISHED
                try {
                    encounter.period.end = ts.timeOfAnEvent.valueAsDate
                } catch (ex: Exception) {
                }
            }
        }
        if (pv1.attendingDoctor != null) {
            for (xcn in pv1.attendingDoctor) {
                val participantComponent = encounter.addParticipant()
                participantComponent.individual = xcNtoReference.transform(xcn)
                participantComponent.addType().addCoding().setCode("ATND")
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType").display =
                    "attender"
            }
        }
        if (pv1.admittingDoctor != null) {
            for (xcn in pv1.admittingDoctor) {
                val participantComponent = encounter.addParticipant()
                participantComponent.individual = xcNtoReference.transform(xcn)
                participantComponent.addType().addCoding().setCode("ADM")
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType").display =
                    "admitter"
            }
        }
        if (pv1.consultingDoctor != null) {
            for (xcn in pv1.consultingDoctor) {
                val participantComponent = encounter.addParticipant()
                participantComponent.individual = xcNtoReference.transform(xcn)
                participantComponent.addType().addCoding().setCode("CON")
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType").display =
                    "consultant"
            }
        }
        if (pv1.referringDoctor != null) {
            for (xcn in pv1.referringDoctor) {
                val participantComponent = encounter.addParticipant()
                participantComponent.individual = xcNtoReference.transform(xcn)
                participantComponent.addType().addCoding().setCode("REF")
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType").display =
                    "referrer"
            }
        }
        if (pv1.otherHealthcareProvider != null) {
            for (xcn in pv1.otherHealthcareProvider) {
                val participantComponent = encounter.addParticipant()
                participantComponent.individual = xcNtoReference.transform(xcn)
                participantComponent.addType().addCoding().setCode("PART")
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType").display =
                    "Participation"
            }
        }
        if (pv1.hospitalService != null) {
            encounter.serviceType = CodeableConcept().addCoding(Coding()
                .setCode(pv1.hospitalService.value)
                .setSystem("https://fhir.nhs.uk/CodeSystem/NHSDataModelAndDictionary-treatment-function"))
        }

        if (pv1.admitSource != null) {
            encounter.hospitalization.admitSource.addCoding().setCode(pv1.admitSource.value).system =
                "https://fhir.nhs.uk/CodeSystem/UKCore-SourceOfAdmission"
        }
        if (pv1.dischargeDisposition != null && (pv1.dischargeDisposition.value != null) && (pv1.dischargeDisposition.value.isEmpty())) {
            // Note using disposition not location.
            encounter.hospitalization.dischargeDisposition.addCoding()
                .setSystem("https://fhir.hl7.org.uk/CodeSystem/UKCore-DischargeMethodEngland").code =
                pv1.dischargeDisposition.value
        }
        /*
       TODO Check if error in HSCIC Specification

       if (pv1.getDischargedToLocation() != null) {
            // Note using disposition not location.
            encounter.getHospitalization().getDischargeDisposition().addCoding()
                    .setSystem("https://fhir.nhs.uk/R4/CodeSystem/UKCore-DischargeMethod")
                    .setCode(pv1.getDischargedToLocation().getDischargeLocation().getValue());
        }*/

        return encounter
    }


}
