package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.segment.PID
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.*

class PIDtoFHIRPatient : Transformer<PID, Patient> {

    var xcNtoReference: XCNtoFHIRReference = XCNtoFHIRReference()
    var xpNtoHumanName = XPNtoHumanName()
    var xaDtoAddressTransform = XADtoAddressTransform()
    var xtNtoContactPointTransform = XTNtoContactPointTransform()

    override fun transform(pid: PID): Patient {
        val patient = Patient()


        for (cx in pid.patientIdentifierList) {
            val identifier = Identifier().setValue(cx.idNumber.value)
            if (cx.assigningAuthority != null && (cx.assigningAuthority.namespaceID != null) && (cx.assigningAuthority.namespaceID.value != null)) {

                // Default
                identifier.system =
                   // HL7V2Properties.getServerIdentifierPrefix() + "/" +
                            cx.assigningAuthority.namespaceID.value.replace(" ","")
                if (cx.assigningAuthority.namespaceID.value == "NHS" || cx.assigningAuthority.namespaceID.value == "NH") {
                    identifier.system = "https://fhir.nhs.uk/Id/nhs-number"
                }
                if (cx.assigningAuthority.namespaceID.value.equals("154")) {
                    identifier.system = "https://cardiff.nhs.uk/Id/mrn"
                }
            }
            patient.addIdentifier(identifier)
        }
        for (xpn in pid.patientName) {
            patient.addName(xpNtoHumanName.transform(xpn))
        }

        if (pid.dateTimeOfBirth != null) {
            try {
                patient.birthDate = pid.dateTimeOfBirth.time.valueAsDate
            } catch (ex: Exception) {
                println(ex.message)
            }
        }
        if (pid.administrativeSex != null && pid.administrativeSex.value != null) {
            val gender = pid.administrativeSex
            when (gender.value) {
                "X" -> patient.gender = Enumerations.AdministrativeGender.UNKNOWN
                "9" -> patient.gender = Enumerations.AdministrativeGender.OTHER
                "1" -> patient.gender = Enumerations.AdministrativeGender.MALE
                "2" -> patient.gender = Enumerations.AdministrativeGender.FEMALE
            }
        }
        for (xtn in pid.phoneNumberHome) {
            val contact: ContactPoint = xtNtoContactPointTransform.transform(xtn)
            contact.use = ContactPoint.ContactPointUse.HOME
            patient.addTelecom(contact)
        }
        for (xtn in pid.phoneNumberBusiness) {
            val contact: ContactPoint = xtNtoContactPointTransform.transform(xtn)
            contact.use = ContactPoint.ContactPointUse.WORK
            patient.addTelecom(contact)
        }
        for (xad in pid.patientAddress) {
            patient.addAddress(xaDtoAddressTransform.transform(xad))
        }

        return patient
    }


}
