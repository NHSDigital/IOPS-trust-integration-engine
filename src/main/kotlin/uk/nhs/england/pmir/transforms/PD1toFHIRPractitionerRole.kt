package uk.nhs.england.pmir.transforms

import ca.uhn.hl7v2.model.v24.segment.PD1
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.PractitionerRole
import org.hl7.fhir.r4.model.Reference
import uk.nhs.england.pmir.util.FhirSystems

class PD1toFHIRPractitionerRole : Transformer<PD1, PractitionerRole> {
    override fun transform(pd1: PD1?): PractitionerRole {
        var practitionerRole = PractitionerRole()
        if (pd1 == null) return practitionerRole

        if (pd1.patientPrimaryFacility != null && pd1.patientPrimaryFacility.size>0) {
            practitionerRole.organization = Reference().setIdentifier(
                Identifier().setSystem(FhirSystems.ODS_CODE)
                    .setValue(pd1.patientPrimaryFacility[0].idNumber.value)

            ).setDisplay(pd1.patientPrimaryFacility[0].organizationName.value)
        }
        if (pd1.patientPrimaryCareProviderNameIDNo != null && pd1.patientPrimaryCareProviderNameIDNo.size>0) {
            practitionerRole.practitioner = Reference().setIdentifier(
                Identifier().setSystem(FhirSystems.NHS_GMP_NUMBER)
                    .setValue(pd1.patientPrimaryCareProviderNameIDNo[0].idNumber.value)

            ).setDisplay(pd1.patientPrimaryCareProviderNameIDNo[0].familyName.surname.value)
        }
        return practitionerRole
    }

}
