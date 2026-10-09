package com.geovannycode.ecoeats.shared.api

import io.swagger.v3.oas.annotations.media.Schema

internal data class FieldError(
    @field:Schema(
        description = "Nombre del campo JSON que falló la validación",
        example = "correo",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val field: String,
    @field:Schema(
        description = "Mensaje de validación en español",
        example = "El correo debe ser válido",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val message: String,
)
