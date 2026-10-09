package com.geovannycode.ecoeats.shared.web

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
internal class WebFilterConfiguration {
    @Bean
    fun rateLimitRegistration(filter: RateLimitFilter): FilterRegistrationBean<RateLimitFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun bodyLimitRegistration(filter: JsonBodyLimitFilter): FilterRegistrationBean<JsonBodyLimitFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }
}
