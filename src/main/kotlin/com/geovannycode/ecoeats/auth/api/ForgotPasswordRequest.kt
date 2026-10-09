package com.geovannycode.ecoeats.auth.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

internal data class ForgotPasswordRequest(
    @field:NotBlank(message = "El email es obligatorio")
    @field:Email(message = "El email debe ser válido")
    val email: String,
)
