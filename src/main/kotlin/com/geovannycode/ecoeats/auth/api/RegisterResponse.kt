package com.geovannycode.ecoeats.auth.api

import io.swagger.v3.oas.annotations.media.Schema

internal data class RegisterResponse(
    @field:Schema(description = "Usuario registrado, sin contraseña", example = "", requiredMode = Schema.RequiredMode.REQUIRED)
    val user: RegisteredUser,
)
