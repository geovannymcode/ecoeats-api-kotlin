package com.geovannycode.ecoeats.auth.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

internal data class RegisterRequest(
    @JsonProperty("numero_documento")
    @field:NotBlank(message = "El número de documento es obligatorio")
    @field:Size(max = 30, message = "El número de documento no puede superar 30 caracteres")
    @field:Schema(
        description = "Número de documento; en JSON se llama numero_documento",
        example = "1032456789",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val numeroDocumento: String,
    @field:NotBlank(message = "Los nombres son obligatorios")
    @field:Size(max = 100, message = "Los nombres no pueden superar 100 caracteres")
    @field:Schema(description = "Nombres del usuario", example = "Geovanny", requiredMode = Schema.RequiredMode.REQUIRED)
    val nombres: String,
    @field:NotBlank(message = "Los apellidos son obligatorios")
    @field:Size(max = 100, message = "Los apellidos no pueden superar 100 caracteres")
    @field:Schema(description = "Apellidos del usuario", example = "Mendoza", requiredMode = Schema.RequiredMode.REQUIRED)
    val apellidos: String,
    @field:NotBlank(message = "El correo es obligatorio")
    @field:Email(message = "El correo debe ser válido")
    @field:Size(max = 150, message = "El correo no puede superar 150 caracteres")
    @field:Schema(
        description = "Email; registro utiliza correo, login utiliza email",
        example = "geovanny@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val correo: String,
    @field:NotBlank(message = "La contraseña es obligatoria")
    @field:Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    @field:BcryptPassword
    @field:Schema(
        description = "Contraseña: registro mínimo 6 caracteres y máximo 72 bytes UTF-8",
        example = "Ejemplo123!",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val password: String,
)
