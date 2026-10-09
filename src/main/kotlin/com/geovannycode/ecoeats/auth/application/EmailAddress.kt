package com.geovannycode.ecoeats.auth.application

import java.util.Locale

internal fun String.normalizedEmail(): String = trim().lowercase(Locale.ROOT)

internal fun String.maskedEmail(): String {
    val at = indexOf('@')
    if (at <= 0) return "***"
    val initial = first().takeIf { it.isLetterOrDigit() } ?: '*'
    val domain = substring(at + 1).replace("\r", "").replace("\n", "")
    return "$initial***@$domain"
}
