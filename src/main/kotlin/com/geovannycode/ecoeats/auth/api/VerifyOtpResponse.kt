package com.geovannycode.ecoeats.auth.api

import io.swagger.v3.oas.annotations.media.Schema

internal data class VerifyOtpResponse(
    @field:Schema(
        description = "Indica si el OTP es válido; false sigue siendo una respuesta HTTP 200",
        example = "true",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val valid: Boolean,
)
