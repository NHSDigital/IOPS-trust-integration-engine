package uk.nhs.nhsdigital.integrationengine.transforms

import ca.uhn.hl7v2.model.v24.segment.PV1
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Encounter

class PV1toFHIREncounter : Transformer<PV1, Encounter> {

    var xcNtoReference: XCNtoFHIRReference = XCNtoFHIRReference()

    override fun transform(pv1: PV1): Encounter {
        var encounter = Encounter()

        if (pv1.visitNumber != null) {
            encounter.addIdentifier().setValue(pv1.visitNumber.id.value).system =
                "http://terminology.hl7.org/CodeSystem/v2-0203"
        }
        if (pv1.alternateVisitID != null) {
            encounter.addIdentifier().value = pv1.alternateVisitID.id.value
        }
        if (pv1.patientClass.value != null) {
            when (pv1.patientClass.value) {
                "E" -> {
                    encounter.class_ = Coding().setCode("EMER").setDisplay("emergency")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                    encounter.status = Encounter.EncounterStatus.INPROGRESS
                }

                "O" -> {
                    encounter.class_ = Coding().setCode("AMB").setDisplay("ambulatory")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                    encounter.status = Encounter.EncounterStatus.INPROGRESS
                }

                "I" -> {
                    encounter.class_ = Coding().setCode("IMP").setDisplay("inpatient encounter")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                    encounter.status = Encounter.EncounterStatus.INPROGRESS
                }

                "P" -> {
                    encounter.class_ = Coding().setCode("PRENC").setDisplay("pre-admission")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                    encounter.status = Encounter.EncounterStatus.PLANNED
                }
            }
        }
        if (pv1.admissionType != null) {
            encounter.addType().addCoding().setCode(pv1.admissionType.value).system =
                "https://fhir.nhs.uk/R4/CodeSystem/UKCore-AdmissionMethod"
        }

        if (pv1.admitDateTime != null) {
            try {
                encounter.period.start = pv1.admitDateTime.timeOfAnEvent.valueAsDate
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
            encounter.addType().addCoding().setCode(pv1.hospitalService.value).system =
                "https://fhir.nhs.uk/STU3/CodeSystem/DCH-Specialty-1"
        }

        if (pv1.admitSource != null) {
            encounter.hospitalization.admitSource.addCoding().setCode(pv1.admitSource.value).system =
                "https://fhir.nhs.uk/R4/CodeSystem/UKCore-SourceOfAdmission"
        }
        if (pv1.dischargeDisposition != null && (pv1.dischargeDisposition.value != null) && (pv1.dischargeDisposition.value.isEmpty())) {
            // Note using disposition not location.
            encounter.hospitalization.dischargeDisposition.addCoding()
                .setSystem("https://fhir.nhs.uk/R4/CodeSystem/UKCore-DischargeMethod").code =
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
