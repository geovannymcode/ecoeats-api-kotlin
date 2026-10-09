package com.geovannycode.ecoeats.auth.domain

internal sealed interface RegistrationResult {
    data class Registered(
        val user: UserEntity,
    ) : RegistrationResult

    data object EmailTaken : RegistrationResult

    data object DocumentTaken : RegistrationResult
}
