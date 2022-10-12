package uk.nhs.nhsdigital.integrationengine.transforms;

import ca.uhn.hl7v2.model.v24.datatype.XTN;
import org.apache.commons.collections4.Transformer;
import org.hl7.fhir.r4.model.ContactPoint;

public class XTNtoContactPointTransform implements Transformer<XTN, ContactPoint> {
    @Override
    public ContactPoint transform(XTN xtn) {
        ContactPoint contactPoint = new ContactPoint();
        if (xtn.getPhoneNumber() != null) {
            contactPoint.setValue(xtn.getAnyText().getValue());
            contactPoint.setSystem(ContactPoint.ContactPointSystem.PHONE);
        }
        if (xtn.get9999999X99999CAnyText()!=null) {
            contactPoint.setValue(xtn.get9999999X99999CAnyText().getValue());
            contactPoint.setSystem(ContactPoint.ContactPointSystem.PHONE);
        }

        return contactPoint;
    }
}
