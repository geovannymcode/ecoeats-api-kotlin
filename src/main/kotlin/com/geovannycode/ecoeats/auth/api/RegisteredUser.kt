package com.geovannycode.ecoeats.auth.api

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

internal data class RegisteredUser(
    val id: UUID,
    @JsonProperty("numero_documento") val numeroDocumento: String,
    val nombres: String,
    val apellidos: String,
    val correo: String,
)
