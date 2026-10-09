package com.geovannycode.ecoeats.shared.security

import com.geovannycode.ecoeats.shared.config.EcoEatsProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration(proxyBeanMethods = false)
internal class SecurityConfiguration {
    @Bean
    fun securityFilterChain(http: HttpSecurity, converter: ExistingUserJwtConverter,
        entryPoint: ApiAuthenticationEntryPoint, deniedHandler: ApiAccessDeniedHandler): SecurityFilterChain = http
        .csrf { it.disable() }
        .cors { }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers(HttpMethod.POST, "/api/securities/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/health", "/docs", "/docs/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/dish", "/api/dish/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/dish", "/api/dish/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/dish", "/api/dish/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/dish", "/api/dish/**").hasRole("ADMIN")
                .anyRequest().authenticated()
        }
        .exceptionHandling { it.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler) }
        .oauth2ResourceServer {
            it.jwt { jwt -> jwt.jwtAuthenticationConverter(converter) }
                .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler)
        }
        .build()

    @Bean
    fun corsConfigurationSource(properties: EcoEatsProperties): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", CorsConfiguration().apply {
                allowedOrigins = properties.cors.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("Authorization", "Content-Type", "Accept")
                allowCredentials = false
                maxAge = 3600
            })
        }
}
