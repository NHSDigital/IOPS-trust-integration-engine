package uk.nhs.nhsdigital.pmir

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.ServletComponentScan
import uk.nhs.nhsdigital.pmir.configuration.*

@SpringBootApplication
@ServletComponentScan
@EnableConfigurationProperties(FHIRServerProperties::class)
open class TIEApplication

fun main(args: Array<String>) {
    runApplication<TIEApplication>(*args)
}
