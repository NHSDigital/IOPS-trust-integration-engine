package uk.nhs.nhsdigital.integrationengine

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.support.IValidationSupport
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import org.hl7.fhir.utilities.npm.NpmPackage
import org.springframework.beans.factory.annotation.Qualifier
import uk.nhs.nhsdigital.integrationengine.configuration.FHIRServerProperties
import uk.nhs.nhsdigital.integrationengine.provider.*
import uk.nhs.nhsdigital.integrationengine.interceptor.AWSAuditEventLoggingInterceptor
import java.util.*
import javax.servlet.annotation.WebServlet

@WebServlet("/FHIR/R4/*", loadOnStartup = 1)
class FHIRR4RestfulServer(
    @Qualifier("R4") fhirContext: FhirContext,
    public val fhirServerProperties: FHIRServerProperties
) : RestfulServer(fhirContext) {

    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))


     //   registerInterceptor(CapabilityStatementInterceptor(this.fhirContext,npmPackages, supportChain, fhirServerProperties))

        val awsAuditEventLoggingInterceptor =
            AWSAuditEventLoggingInterceptor(
                this.fhirContext,
                fhirServerProperties
            )
        interceptorService.registerInterceptor(awsAuditEventLoggingInterceptor)


        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
