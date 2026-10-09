package com.geovannycode.ecoeats.shared.security

import org.springframework.security.authentication.BadCredentialsException

internal class InvalidTokenException : BadCredentialsException("Token inválido")
