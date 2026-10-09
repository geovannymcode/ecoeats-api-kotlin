package com.geovannycode.ecoeats.auth.domain

internal sealed interface PasswordResetResult {
    data object Reset : PasswordResetResult

    data object InvalidOtp : PasswordResetResult
}
