package com.geovannycode.ecoeats.shared.security

import com.geovannycode.ecoeats.shared.api.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
internal class SecurityErrorWriter(
    private val mapper: ObjectMapper,
) {
    fun write(
        response: HttpServletResponse,
        status: Int,
        message: String,
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(mapper.writeValueAsString(ApiResponse.fail(message)))
    }
}
