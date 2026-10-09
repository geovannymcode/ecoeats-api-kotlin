package com.geovannycode.ecoeats.notification

internal interface EmailSender {
    fun sendPasswordResetOtp(
        to: String,
        otp: String,
    )
}
