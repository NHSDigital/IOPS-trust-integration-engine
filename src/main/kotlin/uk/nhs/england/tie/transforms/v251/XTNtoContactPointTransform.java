package uk.nhs.england.tie.transforms.v251;

import ca.uhn.hl7v2.model.v251.datatype.XTN;
import org.apache.commons.collections4.Transformer;
import org.hl7.fhir.r4.model.ContactPoint;

public class XTNtoContactPointTransform implements Transformer<XTN, ContactPoint> {
    @Override
    public ContactPoint transform(XTN xtn) {
        ContactPoint contactPoint = new ContactPoint();
        if (xtn.getTelephoneNumber() != null) {
            contactPoint.setValue(xtn.getAnyText().getValue());
            contactPoint.setSystem(ContactPoint.ContactPointSystem.PHONE);
        }


        return contactPoint;
    }
}
