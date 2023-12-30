package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.segment.OBR
import ca.uhn.hl7v2.model.v251.segment.ORC
import ca.uhn.hl7v2.model.v251.segment.TQ1
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.*


class TQ1toFHIRServiceRequest : Transformer<TQ1, ServiceRequest> {
    var XCNtoFHIRReference = XCNtoFHIRReference()
    fun transform(tQ1: TQ1, serviceRequest: ServiceRequest) {

       if (tQ1.startDateTime !== null) {
           serviceRequest.occurrencePeriod.start = tQ1.startDateTime.time.valueAsDate
       }
        if (tQ1.endDateTime !== null) {
            serviceRequest.occurrencePeriod.end = tQ1.endDateTime.time.valueAsDate
        }
        if (tQ1.priority !== null) {
            tQ1.priority.forEach {
                if (it.identifier !== null) {
                    when (it.identifier.value) {
                        "U" -> {
                            serviceRequest.priority = ServiceRequest.ServiceRequestPriority.URGENT
                        }
                        "S" -> {
                            serviceRequest.priority = ServiceRequest.ServiceRequestPriority.STAT
                        }
                    }
                }
            }
        }
    }

    override fun transform(input: TQ1?): ServiceRequest {
        // TODO("Not yet implemented")
        return ServiceRequest()
    }


}
