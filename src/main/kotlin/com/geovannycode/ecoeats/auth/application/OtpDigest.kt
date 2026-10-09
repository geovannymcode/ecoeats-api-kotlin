package com.geovannycode.ecoeats.auth.application

import java.security.MessageDigest

internal fun String.otpSha256(): String =
    MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8)).toHexString()

internal fun String.matchesOtpHash(hash: String): Boolean = MessageDigest.isEqual(
    otpSha256().toByteArray(Charsets.US_ASCII), hash.toByteArray(Charsets.US_ASCII),
)
