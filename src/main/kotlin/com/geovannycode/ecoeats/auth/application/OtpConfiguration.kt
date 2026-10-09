package com.geovannycode.ecoeats.auth.application

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.SecureRandom

@Configuration(proxyBeanMethods = false)
internal class OtpConfiguration {
    @Bean
    fun otpRandom(): SecureRandom = SecureRandom()
}
