package com.geovannycode.ecoeats.auth.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

internal data class VerifyOtpRequest(
    @field:NotBlank(message = "El email es obligatorio")
    @field:Email(message = "El email debe ser válido")
    val email: String,
    @field:Pattern(regexp = "\\d{6}", message = "El OTP debe tener exactamente 6 dígitos")
    val otp: String,
)
