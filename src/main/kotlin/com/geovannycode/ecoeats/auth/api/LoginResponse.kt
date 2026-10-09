package com.geovannycode.ecoeats.auth.api

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

internal data class LoginResponse(
    @field:Schema(
        description = "Identificador UUID del recurso",
        example = "0199c394-3880-7000-8000-000000000001",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val id: UUID,
    @field:Schema(
        description = "Email; se normaliza con trim y lowercase",
        example = "geovanny@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val email: String,
    @field:Schema(
        description = "JWT de ejemplo no utilizable; copiar el token real del login",
        example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJleGFtcGxlIn0.example-signature",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val token: String,
    @field:Schema(description = "Campo heredado del contrato; puede ser vacío", example = "", requiredMode = Schema.RequiredMode.REQUIRED)
    val tokenFirebaseAuth: String,
)
