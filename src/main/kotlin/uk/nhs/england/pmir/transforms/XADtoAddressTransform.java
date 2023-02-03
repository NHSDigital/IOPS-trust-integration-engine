package uk.nhs.england.pmir.transforms;

import ca.uhn.hl7v2.model.v24.datatype.XAD;
import org.apache.commons.collections4.Transformer;
import org.hl7.fhir.r4.model.Address;

public class XADtoAddressTransform implements Transformer<XAD, Address> {
    @Override
    public Address transform(XAD xad) {
        Address address = new Address();
        if (xad.getStreetAddress() != null) {
            if (xad.getStreetAddress().getDwellingNumber() != null) {
                address.addLine(xad.getStreetAddress().getDwellingNumber().getValue());
            }
            if (xad.getStreetAddress().getStreetName() != null) {
                address.addLine(xad.getStreetAddress().getStreetName().getValue());
            }
        }
        if (xad.getCity()!= null) {
            address.setCity(xad.getCity().getValue());
        }
        if (xad.getStateOrProvince() != null) {
            address.setDistrict(xad.getStateOrProvince().getValue());
        }
        if (xad.getZipOrPostalCode() !=null) {
            address.setPostalCode(xad.getZipOrPostalCode().getValue());
        }
        if (xad.getAddressType() != null && xad.getAddressType().getValue() != null) {
            switch (xad.getAddressType().getValue()) {
                case "H":
                    address.setUse(Address.AddressUse.HOME);
                    break;
                case "B":
                    address.setUse(Address.AddressUse.WORK);
                    break;
                case "C":
                    address.setUse(Address.AddressUse.TEMP);
                    break;
            }
        }
        return address;
    }
}
