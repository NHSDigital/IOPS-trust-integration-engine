package uk.nhs.england.tie.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "fhir")
data class FHIRServerProperties(
    var server: Server
) {
    data class Server(
        var baseUrl: String,
        var name: String,
        var version: String,
        var authorize: String,
        var token: String,
        var introspect: String,
        var smart: Boolean
    )
}
