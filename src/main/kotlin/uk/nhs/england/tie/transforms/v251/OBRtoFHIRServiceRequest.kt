package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.segment.OBR
import ca.uhn.hl7v2.model.v251.segment.ORC
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.*


class OBRtoFHIRServiceRequest : Transformer<OBR, ServiceRequest> {
    var XCNtoFHIRReference = XCNtoFHIRReference()
    override fun transform(obr: OBR): ServiceRequest {
        var serviceRequest= ServiceRequest()
        serviceRequest.intent = ServiceRequest.ServiceRequestIntent.ORDER
        serviceRequest.status = ServiceRequest.ServiceRequestStatus.ACTIVE

        if (obr.placerOrderNumber !== null ) {

            if (obr.placerOrderNumber.entityIdentifier !== null && obr.placerOrderNumber.entityIdentifier.value !== null) {
                serviceRequest.addIdentifier(Identifier().setValue(obr.placerOrderNumber.entityIdentifier.value))
            }
        }
        if (obr.fillerOrderNumber !== null ) {
            if (obr.fillerOrderNumber.entityIdentifier !== null && obr.fillerOrderNumber.entityIdentifier.value !== null) {
                serviceRequest.addIdentifier(Identifier().setValue(obr.fillerOrderNumber.entityIdentifier.value))
            }
        }
        if (obr.universalServiceIdentifier !== null ) {

            if (obr.universalServiceIdentifier.identifier !== null) {
                serviceRequest.code.addCoding(
                    Coding()
                        .setCode(obr.universalServiceIdentifier.identifier.value)
                        .setDisplay(obr.universalServiceIdentifier.text.value)
                )
            }
        }

        if (obr.orderingProvider !== null && obr.orderingProvider.size > 0) {
            serviceRequest.setRequester(XCNtoFHIRReference.transform(obr.orderingProvider[0]))
        }


        return serviceRequest
    }



}
