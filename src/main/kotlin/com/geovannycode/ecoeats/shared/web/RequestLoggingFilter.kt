package com.geovannycode.ecoeats.shared.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
internal class RequestLoggingFilter : OncePerRequestFilter() {
    private val requestLogger = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val requestId = request.getHeader("X-Request-Id")?.takeIf { SAFE_ID.matches(it) } ?: UUID.randomUUID().toString()
        val previous = MDC.get("requestId")
        val start = System.nanoTime()
        MDC.put("requestId", requestId)
        response.setHeader("X-Request-Id", requestId)
        try {
            chain.doFilter(request, response)
        } finally {
            // Log route templates, never query strings, credentials, headers or request/response bodies.
            val route =
                request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString()
                    ?: request.requestURI.takeIf { it in SAFE_PATHS } ?: "[unmatched]"
            requestLogger.info(
                "HTTP request method={} route={} status={} durationMs={} requestId={}",
                request.method.takeIf { it in METHODS } ?: "OTHER",
                route,
                response.status,
                (System.nanoTime() - start) / 1_000_000,
                requestId,
            )
            if (previous == null) MDC.remove("requestId") else MDC.put("requestId", previous)
        }
    }

    companion object {
        private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")
        private val METHODS = setOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
        private val SAFE_PATHS =
            setOf(
                "/health",
                "/api/dish",
                "/api/securities/login",
                "/api/securities/register",
                "/api/securities/forgot-password",
                "/api/securities/verify-otp",
                "/api/securities/reset-password",
            )
    }
}
