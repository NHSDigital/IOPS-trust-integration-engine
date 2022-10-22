package uk.nhs.nhsdigital.pmir.transforms;

import ca.uhn.hl7v2.model.v24.datatype.XPN;
import org.apache.commons.collections4.Transformer;
import org.hl7.fhir.r4.model.HumanName;


public class XPNtoHumanName implements Transformer<XPN, HumanName> {

    @Override
    public HumanName transform(XPN xpn) {
        HumanName name = new HumanName();
        if (xpn.getGivenName() != null) {
            name.addGiven(xpn.getGivenName().getValue());
        }
        if (xpn.getFamilyName() != null) {
            name.setFamily(xpn.getFamilyName().getSurname().getValue());
        }
        if (xpn.getXpn5_PrefixEgDR() != null) {
            name.addPrefix(xpn.getXpn5_PrefixEgDR().getValue());
        }
        return name;
    }
}
