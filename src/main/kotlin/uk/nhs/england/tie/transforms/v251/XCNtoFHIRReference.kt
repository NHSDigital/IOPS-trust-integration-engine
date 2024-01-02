package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.datatype.XCN
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.Reference
import uk.nhs.england.tie.util.FhirSystems

class XCNtoFHIRReference : Transformer<XCN, Reference> {


    override fun transform(xcn: XCN?): Reference {

        val reference = Reference()
        if (xcn!= null) {
            val identifier = reference.identifier

            var name = ""
            if (xcn.getPrefixEgDR() !== null && xcn.getPrefixEgDR().value !== null) {
                name += xcn.getPrefixEgDR().getValue()
            }
            if (xcn.getGivenName() !== null && xcn.getGivenName().value !== null) {
                name += " " + xcn.getGivenName().getValue()
            }
            if (xcn.getFamilyName() !== null && xcn.getFamilyName().surname != null && xcn.getFamilyName().surname.value != null) {
                name += " " + xcn.getFamilyName().getSurname().getValue()
            }
            if (!name.isEmpty()) reference.display = name.trim()
            if (xcn.getIDNumber() != null) {
                identifier.value = xcn.getIDNumber().getValue()
            }
            if (xcn.getAssigningAuthority() != null && xcn.getAssigningAuthority()
                    .getNamespaceID() != null && xcn.getAssigningAuthority().getNamespaceID().getValue() != null
            ) {
                if (xcn.getAssigningAuthority().getNamespaceID().getValue() == "GMC") {
                    identifier.system = FhirSystems.NHS_GMC_NUMBER
                }
                if (xcn.getAssigningAuthority().getNamespaceID().getValue() == "GMP") {
                    identifier.system = FhirSystems.NHS_GMP_NUMBER
                }
            }
        }
        return reference
    }

}
