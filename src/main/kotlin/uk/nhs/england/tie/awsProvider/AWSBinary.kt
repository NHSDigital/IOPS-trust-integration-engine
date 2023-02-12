package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import java.io.File
import java.nio.file.Files
import java.util.*

@Component
class AWSBinary(val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                @Qualifier("R4") val ctx: FhirContext,
                val fhirServerProperties: FHIRServerProperties,
                private val cognitoAuthInterceptor: CognitoAuthInterceptor
) {

    private val log = LoggerFactory.getLogger("FHIRAudit")

    fun get(internalId: IdType): Binary {
        val binary = Binary()
        var path = internalId.value
        if (!path.startsWith("/")) path = "/" + path
        val location = cognitoAuthInterceptor.getBinaryLocation(path)
        println(location.getString("presignedGetUrl"))
        binary.id = location.getString("id")
        binary.contentType = location.getString("contentType")
        val conn = cognitoAuthInterceptor.getBinary(location.getString("presignedGetUrl"))
        binary.data = conn.inputStream.readAllBytes()
        return binary
    }

    public fun create(binary: Binary): MethodOutcome? {

        val json = cognitoAuthInterceptor.postBinaryLocation(binary)
        val location = json.getString("presignedPutUrl")
        binary.id = json.getString("id")
        cognitoAuthInterceptor.postBinary(location,binary.data)
        return MethodOutcome().setResource(binary)
    }

    public fun create(fileName : String): MethodOutcome? {

        var response: MethodOutcome? = null
        val binary = Binary()
        binary.contentType = "application/gzip"
        val json = cognitoAuthInterceptor.postBinaryLocation(binary)
        val location = json.getString("presignedPutUrl")
        binary.id = json.getString("id")
        var file = File(fileName)
        val fileContent: ByteArray = Files.readAllBytes(file.toPath())
        cognitoAuthInterceptor.postBinary(location,fileContent)
        return MethodOutcome().setResource(binary)
    }
}
