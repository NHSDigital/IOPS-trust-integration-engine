package uk.nhsdigital.integrationengine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.ServletComponentScan
import uk.nhsdigital.integrationengine.configuration.*

@SpringBootApplication
@ServletComponentScan
@EnableConfigurationProperties
open class TIEApplication

fun main(args: Array<String>) {
    runApplication<TIEApplication>(*args)
}
