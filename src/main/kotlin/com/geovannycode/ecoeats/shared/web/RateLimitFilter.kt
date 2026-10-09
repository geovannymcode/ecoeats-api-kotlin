package com.geovannycode.ecoeats.shared.web

import com.geovannycode.ecoeats.shared.security.SecurityErrorWriter
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

@Component
internal class RateLimitFilter(
    private val errors: SecurityErrorWriter,
) : OncePerRequestFilter() {
    private val buckets =
        Caffeine
            .newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build<String, Bucket>()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI == "/health"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        // ForwardedHeaderFilter has already resolved remoteAddr; do not parse forwarding headers here.
        val ip = request.remoteAddr
        val policies =
            buildList {
                add("global" to 100L)
                if (request.method == "POST") {
                    when (request.requestURI) {
                        "/api/securities/login", "/api/securities/register" -> add(request.requestURI to 10L)
                        "/api/securities/forgot-password", "/api/securities/verify-otp" -> add(request.requestURI to 5L)
                    }
                }
            }
        for ((policy, capacity) in policies) {
            val bucket =
                buckets.get("$ip:$policy") {
                    Bucket.builder().addLimit { limit -> limit.capacity(capacity).refillGreedy(capacity, Duration.ofMinutes(1)) }.build()
                }
            val probe = bucket.tryConsumeAndReturnRemaining(1)
            if (!probe.isConsumed) {
                response.setHeader("Retry-After", maxOf(1, (probe.nanosToWaitForRefill + 999_999_999) / 1_000_000_000).toString())
                errors.write(response, 429, "Demasiadas solicitudes, intenta más tarde")
                return
            }
        }
        chain.doFilter(request, response)
    }
}
