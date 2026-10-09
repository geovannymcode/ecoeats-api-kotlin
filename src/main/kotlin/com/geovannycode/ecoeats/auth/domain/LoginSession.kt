package com.geovannycode.ecoeats.auth.domain

import java.util.UUID

internal data class LoginSession(
    val id: UUID,
    val email: String,
    val token: String,
    val tokenFirebaseAuth: String,
)
