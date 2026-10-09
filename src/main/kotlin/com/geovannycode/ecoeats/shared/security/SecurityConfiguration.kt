package com.geovannycode.ecoeats.shared.security

import com.geovannycode.ecoeats.shared.config.EcoEatsProperties
import com.geovannycode.ecoeats.shared.web.JsonBodyLimitFilter
import com.geovannycode.ecoeats.shared.web.RateLimitFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration(proxyBeanMethods = false)
internal class SecurityConfiguration {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        converter: ExistingUserJwtConverter,
        entryPoint: ApiAuthenticationEntryPoint,
        deniedHandler: ApiAccessDeniedHandler,
        rateLimit: RateLimitFilter,
        bodyLimit: JsonBodyLimitFilter,
        environment: Environment,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { }
            .addFilterAfter(rateLimit, CorsFilter::class.java)
            .addFilterAfter(bodyLimit, RateLimitFilter::class.java)
            .headers { headers ->
                if (!environment.acceptsProfiles(Profiles.of("prod"))) headers.httpStrictTransportSecurity { it.disable() }
                headers.frameOptions { it.deny() }
                headers.contentTypeOptions { }
                headers.referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER) }
                headers.addHeaderWriter { request, response ->
                    val docs =
                        request.requestURI == "/docs" ||
                            request.requestURI.startsWith("/docs/") ||
                            request.requestURI.startsWith("/swagger-ui/") ||
                            request.requestURI.startsWith("/v3/api-docs")
                    response.setHeader(
                        "Content-Security-Policy",
                        if (docs) {
                            "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; object-src " +
                                "'none'; frame-ancestors 'none'; base-uri 'self'"
                        } else {
                            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'"
                        },
                    )
                }
            }.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(HttpMethod.POST, "/api/securities/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/health", "/docs", "/docs/**", "/v3/api-docs/**", "/swagger-ui/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/dish", "/api/dish/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/dish", "/api/dish/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/dish", "/api/dish/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/dish", "/api/dish/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated()
            }.exceptionHandling { it.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler) }
            .oauth2ResourceServer {
                it
                    .jwt { jwt -> jwt.jwtAuthenticationConverter(converter) }
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(deniedHandler)
            }.build()

    @Bean
    fun corsConfigurationSource(properties: EcoEatsProperties): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(
                "/**",
                CorsConfiguration().apply {
                    allowedOrigins =
                        properties.cors.allowedOrigins
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    allowedHeaders = listOf("Authorization", "Content-Type")
                    exposedHeaders = listOf("X-Request-Id", "Retry-After")
                    allowCredentials = false
                    maxAge = 3600
                },
            )
        }
}
