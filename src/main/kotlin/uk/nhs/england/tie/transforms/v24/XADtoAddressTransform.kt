package uk.nhs.england.tie.transforms.v24

import ca.uhn.hl7v2.model.v24.datatype.XAD
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.Address

class XADtoAddressTransform : Transformer<XAD?, Address?> {
    override fun transform(xad: XAD?): Address? {
        val address: Address = Address()
        if (xad != null) {
            if (xad.streetAddress != null) {
                if (xad.streetAddress.dwellingNumber != null) {
                    address.addLine(xad.streetAddress.dwellingNumber.value)
                }
                if (xad.streetAddress.streetName != null) {
                    address.addLine(xad.streetAddress.streetName.value)
                }
            }

            if (xad.city != null) {
                address.setCity(xad.city.value)
            }
            if (xad.stateOrProvince != null) {
                address.setDistrict(xad.stateOrProvince.value)
            }
            if (xad.zipOrPostalCode != null) {
                address.setPostalCode(xad.zipOrPostalCode.value)
            }
            if (xad.addressType != null && xad.addressType.value != null) {
                when (xad.addressType.value) {
                    "H" -> address.setUse(Address.AddressUse.HOME)
                    "B" -> address.setUse(Address.AddressUse.WORK)
                    "C" -> address.setUse(Address.AddressUse.TEMP)
                }
            }
        }
        return address
    }
}
