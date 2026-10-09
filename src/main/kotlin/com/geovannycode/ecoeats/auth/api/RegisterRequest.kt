package com.geovannycode.ecoeats.auth.api

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

internal data class RegisterRequest(
    @JsonProperty("numero_documento")
    @field:NotBlank(message = "El número de documento es obligatorio")
    @field:Size(max = 30, message = "El número de documento no puede superar 30 caracteres")
    val numeroDocumento: String,
    @field:NotBlank(message = "Los nombres son obligatorios")
    @field:Size(max = 100, message = "Los nombres no pueden superar 100 caracteres")
    val nombres: String,
    @field:NotBlank(message = "Los apellidos son obligatorios")
    @field:Size(max = 100, message = "Los apellidos no pueden superar 100 caracteres")
    val apellidos: String,
    @field:NotBlank(message = "El correo es obligatorio")
    @field:Email(message = "El correo debe ser válido")
    @field:Size(max = 150, message = "El correo no puede superar 150 caracteres")
    val correo: String,
    @field:NotBlank(message = "La contraseña es obligatoria")
    @field:Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    @field:BcryptPassword
    val password: String,
)
