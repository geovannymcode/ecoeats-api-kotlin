package com.geovannycode.ecoeats.notification

import com.geovannycode.ecoeats.shared.config.EcoEatsProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration(proxyBeanMethods = false)
internal class EmailConfiguration {
    @Bean
    fun emailSender(properties: EcoEatsProperties, environment: Environment, builder: RestClient.Builder): EmailSender {
        val resend = properties.mail.resend
        if (environment.matchesProfiles("local") && resend.apiKey.isBlank()) return LoggingEmailSender()
        require(resend.apiKey.isNotBlank()) { "Resend API key is required outside local development" }
        val client = resendClientBuilder(resend, builder).build()
        val template = ClassPathResource("templates/email/password-reset-otp.html")
            .inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return ResendEmailSender(client, resend.fromEmail, resend.fromName, template)
    }

    fun resendClientBuilder(resend: EcoEatsProperties.Resend, builder: RestClient.Builder): RestClient.Builder {
        val requestFactory = JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        ).apply { setReadTimeout(Duration.ofSeconds(10)) }
        return builder.clone().baseUrl("https://api.resend.com")
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${resend.apiKey}")
    }
}
