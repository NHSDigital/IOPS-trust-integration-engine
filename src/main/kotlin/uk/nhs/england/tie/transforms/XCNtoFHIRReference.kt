package uk.nhs.england.tie.transforms

import ca.uhn.hl7v2.model.v24.datatype.XCN
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.Reference
import uk.nhs.england.tie.util.FhirSystems

class XCNtoFHIRReference : Transformer<XCN, Reference> {


    override fun transform(xcn: XCN?): Reference {

        val reference = Reference()
        if (xcn!= null) {
            val identifier = reference.identifier

            var name = ""
            if (xcn.getPrefixEgDR() != null) {
                name += xcn.getPrefixEgDR().getValue()
            }
            if (xcn.getGivenName() != null) {
                name += " " + xcn.getGivenName().getValue()
            }
            if (xcn.getFamilyName() != null) {
                name += " " + xcn.getFamilyName().getSurname().getValue()
            }
            reference.display = name.trim()
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
