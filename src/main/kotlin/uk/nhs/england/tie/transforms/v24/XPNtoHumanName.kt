package uk.nhs.england.tie.transforms.v24

import ca.uhn.hl7v2.model.v24.datatype.XPN
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.HumanName


class XPNtoHumanName : Transformer<XPN?, HumanName?> {
    override fun transform(xpn: XPN?): HumanName {
        val name: HumanName = HumanName()
        if (xpn != null) {
            if (xpn.givenName != null) {
                name.addGiven(xpn.givenName.value)
            }
            if (xpn.familyName != null) {
                name.setFamily(xpn.familyName.surname.value)
            }
            if (xpn.xpn5_PrefixEgDR != null) {
                name.addPrefix(xpn.xpn5_PrefixEgDR.value)
            }
        }
        return name
    }
}
