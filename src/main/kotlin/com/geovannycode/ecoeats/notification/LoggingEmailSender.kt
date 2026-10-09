package com.geovannycode.ecoeats.notification

import com.geovannycode.ecoeats.auth.application.maskedEmail
import org.slf4j.LoggerFactory

// Instantiated only for the local profile with an empty Resend API key.
internal class LoggingEmailSender : EmailSender {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun sendPasswordResetOtp(
        to: String,
        otp: String,
    ) {
        logger.info("OTP enviado a {}", to.maskedEmail())
    }
}
