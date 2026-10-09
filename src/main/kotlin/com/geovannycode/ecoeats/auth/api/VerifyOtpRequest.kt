package com.geovannycode.ecoeats.auth.api

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

internal data class VerifyOtpRequest(
    @field:NotBlank(message = "El email es obligatorio")
    @field:Email(message = "El email debe ser válido")
    @field:Schema(
        description = "Email; se normaliza con trim y lowercase",
        example = "geovanny@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val email: String,
    @field:Pattern(regexp = "\\d{6}", message = "El OTP debe tener exactamente 6 dígitos")
    @field:Schema(
        description = "Código de seis dígitos; vence en 15 minutos y se bloquea tras cinco intentos fallidos",
        example = "123456",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val otp: String,
)
