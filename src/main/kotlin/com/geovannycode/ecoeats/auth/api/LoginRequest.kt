package com.geovannycode.ecoeats.auth.api

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

internal data class LoginRequest(
    @field:NotBlank(message = "El email es obligatorio")
    @field:Email(message = "El email debe ser válido")
    @field:Schema(
        description = "Email; se normaliza con trim y lowercase",
        example = "geovanny@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val email: String,
    @field:NotBlank(message = "La contraseña es obligatoria")
    @field:Schema(
        description = "Contraseña: registro mínimo 6 caracteres y máximo 72 bytes UTF-8",
        example = "Ejemplo123!",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val password: String,
)
