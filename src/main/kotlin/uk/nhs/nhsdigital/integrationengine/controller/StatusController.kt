package uk.nhs.nhsdigital.integrationengine.controller

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StatusController {
    @GetMapping("_status")
    fun validate(): String {
        return "Validator is alive"
    }
}
