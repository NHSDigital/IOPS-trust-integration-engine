package uk.nhs.england.tie.transforms.v24

import ca.uhn.hl7v2.model.v24.datatype.XTN
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.ContactPoint


class XTNtoContactPointTransform : Transformer<XTN?, ContactPoint?> {
    override fun transform(xtn: XTN?): ContactPoint {
        val contactPoint: ContactPoint = ContactPoint()
        if (xtn != null) {
            if (xtn.phoneNumber != null) {
                contactPoint.setValue(xtn.anyText.value)
                contactPoint.setSystem(ContactPoint.ContactPointSystem.PHONE)
            }

            if (xtn.get9999999X99999CAnyText() != null) {
                contactPoint.setValue(xtn.get9999999X99999CAnyText().value)
                contactPoint.setSystem(ContactPoint.ContactPointSystem.PHONE)
            }
        }

        return contactPoint
    }
}
