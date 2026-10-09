package com.geovannycode.ecoeats.auth.domain

internal sealed interface LoginResult {
    data class Authenticated(val response: LoginSession) : LoginResult
    data object InvalidCredentials : LoginResult
}
