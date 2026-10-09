package com.geovannycode.ecoeats.shared.api

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

internal data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    @get:JsonInclude(JsonInclude.Include.ALWAYS) val data: T?,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val timestamp: Instant? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val errors: List<FieldError>? = null,
) {
    companion object {
        fun <T> ok(data: T?, message: String = "OK"): ApiResponse<T> = ApiResponse(true, message, data)

        fun fail(message: String, errors: List<FieldError>? = null): ApiResponse<Nothing> =
            ApiResponse(false, message, null, Instant.now(), errors)
    }
}
