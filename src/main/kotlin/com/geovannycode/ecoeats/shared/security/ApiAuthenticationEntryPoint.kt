package com.geovannycode.ecoeats.shared.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
internal class ApiAuthenticationEntryPoint(
    private val errors: SecurityErrorWriter,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val invalidUser = generateSequence<Throwable>(exception) { it.cause }.any { it is InvalidTokenException }
        errors.write(response, 401, if (invalidUser) "Token inválido" else "No autorizado")
    }
}
