package com.geovannycode.ecoeats.shared.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
internal class ApiAccessDeniedHandler(
    private val errors: SecurityErrorWriter,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AccessDeniedException,
    ) {
        errors.write(response, 403, "Acceso denegado")
    }
}
