package com.geovannycode.ecoeats.auth.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

internal data class RegisteredUser(
    @field:Schema(
        description = "Identificador UUID del recurso",
        example = "0199c394-3880-7000-8000-000000000001",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val id: UUID,
    @JsonProperty("numero_documento")
    @field:Schema(
        description = "Número de documento; en JSON se llama numero_documento",
        example = "1032456789",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val numeroDocumento: String,
    @field:Schema(description = "Nombres del usuario", example = "Geovanny", requiredMode = Schema.RequiredMode.REQUIRED)
    val nombres: String,
    @field:Schema(description = "Apellidos del usuario", example = "Mendoza", requiredMode = Schema.RequiredMode.REQUIRED)
    val apellidos: String,
    @field:Schema(
        description = "Email; registro utiliza correo, login utiliza email",
        example = "geovanny@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val correo: String,
)
