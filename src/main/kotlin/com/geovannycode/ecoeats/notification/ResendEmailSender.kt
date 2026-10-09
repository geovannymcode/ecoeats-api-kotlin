package com.geovannycode.ecoeats.notification

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

internal class ResendEmailSender(
    private val client: RestClient,
    private val fromEmail: String,
    private val fromName: String,
    private val template: String,
) : EmailSender {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun sendPasswordResetOtp(
        to: String,
        otp: String,
    ) {
        require(otp.matches(Regex("[0-9]{6}"))) { "OTP must contain six digits" }
        val payload =
            ResendEmailRequest(
                from = "$fromName <$fromEmail>",
                to = listOf(to),
                subject = "EcoEats - Código de recuperación de contraseña",
                html = template.replace("{{otp}}", otp),
            )
        try {
            client
                .post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity()
        } catch (exception: RestClientResponseException) {
            logger.warn("Resend email delivery failed: status={}", exception.statusCode.value())
            throw EmailDeliveryException()
        } catch (_: RestClientException) {
            logger.warn("Resend email delivery failed: transport error")
            throw EmailDeliveryException()
        }
    }
}

private data class ResendEmailRequest(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String,
)
