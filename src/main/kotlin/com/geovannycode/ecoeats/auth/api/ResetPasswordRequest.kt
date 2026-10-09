package com.geovannycode.ecoeats.auth.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

internal data class ResetPasswordRequest(
    @field:NotBlank(message = "El email es obligatorio")
    @field:Email(message = "El email debe ser válido")
    val email: String,
    @field:Pattern(regexp = "\\d{6}", message = "El OTP debe tener exactamente 6 dígitos")
    val otp: String,
    @field:NotBlank(message = "La nueva contraseña es obligatoria")
    @field:Size(min = 6, message = "La nueva contraseña debe tener al menos 6 caracteres")
    @field:BcryptPassword
    val newPassword: String,
)
