package uk.nhs.england.pmir.transforms

import ca.uhn.hl7v2.model.v24.segment.PV1
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.*
import uk.nhs.england.pmir.util.FhirSystems

class PV1toFHIRAppointment : Transformer<PV1, Appointment> {

    var xcNtoReference: XCNtoFHIRReference = XCNtoFHIRReference()

    override fun transform(pv1: PV1): Appointment {
        var appointment = Appointment()
        var odsCode :String? = null
        if (pv1.assignedPatientLocation != null ) {
            /*if (!pv1.assignedPatientLocation.pointOfCare.isEmpty) {
                appointment.setServiceProvider(
                    Reference().setIdentifier(Identifier().setSystem(FhirSystems.ODS_CODE).setValue(pv1.assignedPatientLocation.pointOfCare.value))
                )
                odsCode = pv1.assignedPatientLocation.pointOfCare.value
            }*/
            if (!pv1.assignedPatientLocation.facility.isEmpty) {
                val location =
                appointment.participant.add(Appointment.AppointmentParticipantComponent()
                    .setActor(
                    Reference().setIdentifier(Identifier()
                        .setSystem(FhirSystems.ODS_SITE_CODE)
                        .setValue(pv1.assignedPatientLocation.facility.namespaceID.value)))
                )
            }
        }
        if (pv1.visitNumber != null) {
            if (odsCode == null) {
                appointment.addIdentifier().setValue(pv1.visitNumber.id.value).system =
                    "http://terminology.hl7.org/CodeSystem/v2-0203"
            } else {
                appointment.addIdentifier().setValue(pv1.visitNumber.id.value).system =
                    "https://fhir.nhs.uk/" + odsCode + "/Id/Appointment"
            }
        }
        if (pv1.alternateVisitID != null) {
            appointment.addIdentifier().value = pv1.alternateVisitID.id.value
        }
        /*
        if (pv1.patientClass.value != null) {
            when (pv1.patientClass.value) {
                "E" -> {
                    appointment.class_ = Coding().setCode("EMER").setDisplay("emergency")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                }

                "O" -> {
                    appointment.class_ = Coding().setCode("AMB").setDisplay("ambulatory")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")

                }

                "I" -> {
                    appointment.class_ = Coding().setCode("IMP").setDisplay("inpatient appointment")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")

                }

                "P" -> {
                    appointment.class_ = Coding().setCode("PRENC").setDisplay("pre-admission")
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                    appointment.status = Appointment.AppointmentStatus.PLANNED
                }
            }
        }

         */
        if (pv1.admissionType != null) {
            var admissionMethod = Extension().setUrl("https://fhir.hl7.org.uk/StructureDefinition/Extension-UKCore-AdmissionMethod")
                .setValue(CodeableConcept().addCoding(
                    Coding().setSystem("https://fhir.hl7.org.uk/CodeSystem/UKCore-AdmissionMethodEngland").setCode(pv1.admissionType.value)

                ))
            appointment.addExtension(admissionMethod)
        }

        if (pv1.admitDateTime != null) {
            try {
                appointment.start = pv1.admitDateTime.timeOfAnEvent.valueAsDate
                appointment.status = Appointment.AppointmentStatus.BOOKED
            } catch (ex: Exception) {
            }
        }
        if (pv1.dischargeDateTime != null) {
            for (ts in pv1.dischargeDateTime) {
                appointment.status = Appointment.AppointmentStatus.FULFILLED
                try {
                    appointment.end = ts.timeOfAnEvent.valueAsDate
                } catch (ex: Exception) {
                }
            }
        }
        if (pv1.attendingDoctor != null) {
            for (xcn in pv1.attendingDoctor) {
                val participantComponent = appointment.addParticipant()
                participantComponent.actor = xcNtoReference.transform(xcn)
                participantComponent.addType().addCoding().setCode("ATND")
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType").display =
                    "attender"
            }
        }
        if (pv1.admittingDoctor != null) {
            for (xcn in pv1.admittingDoctor) {
                val participantComponent = appointment.addParticipant()
                participantComponent.actor = xcNtoReference.transform(xcn)
                participantComponent.addType().addCoding().setCode("ADM")
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType").display =
                    "admitter"
            }
        }
        if (pv1.consultingDoctor != null) {
            for (xcn in pv1.consultingDoctor) {
                val participantComponent = appointment.addParticipant()
                participantComponent.actor = xcNtoReference.transform(xcn)
                participantComponent.addType().addCoding().setCode("CON")
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType").display =
                    "consultant"
            }
        }
        if (pv1.referringDoctor != null) {
            for (xcn in pv1.referringDoctor) {
                val participantComponent = appointment.addParticipant()
                participantComponent.actor = xcNtoReference.transform(xcn)
                participantComponent.addType().addCoding().setCode("REF")
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType").display =
                    "referrer"
            }
        }
        if (pv1.otherHealthcareProvider != null) {
            for (xcn in pv1.otherHealthcareProvider) {
                val participantComponent = appointment.addParticipant()
                participantComponent.actor = xcNtoReference.transform(xcn)
                participantComponent.addType().addCoding().setCode("PART")
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ParticipationType").display =
                    "Participation"
            }
        }


        /*
       TODO Check if error in HSCIC Specification

       if (pv1.getDischargedToLocation() != null) {
            // Note using disposition not location.
            appointment.getHospitalization().getDischargeDisposition().addCoding()
                    .setSystem("https://fhir.nhs.uk/R4/CodeSystem/UKCore-DischargeMethod")
                    .setCode(pv1.getDischargedToLocation().getDischargeLocation().getValue());
        }*/

        return appointment
    }


}
