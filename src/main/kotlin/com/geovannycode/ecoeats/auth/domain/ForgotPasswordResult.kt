package com.geovannycode.ecoeats.auth.domain

internal sealed interface ForgotPasswordResult {
    data object Sent : ForgotPasswordResult

    data object UserNotFound : ForgotPasswordResult
}
