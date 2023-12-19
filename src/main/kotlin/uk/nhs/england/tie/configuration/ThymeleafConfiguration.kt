package uk.nhs.england.tie.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

@Configuration
class ThymeleafConfiguration {
    @Bean
    fun emailTemplateResolver(): ClassLoaderTemplateResolver {
        val emailTemplateResolver = ClassLoaderTemplateResolver()
        emailTemplateResolver.prefix = "templates/"
        emailTemplateResolver.setTemplateMode("HTML5")
        emailTemplateResolver.suffix = ".html"
        emailTemplateResolver.setTemplateMode("XHTML")
        emailTemplateResolver.characterEncoding = "UTF-8"
        emailTemplateResolver.order = 1
        return emailTemplateResolver
    }
}
