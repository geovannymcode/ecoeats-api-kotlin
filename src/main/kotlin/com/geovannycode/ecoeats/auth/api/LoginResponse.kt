package com.geovannycode.ecoeats.auth.api

import java.util.UUID

internal data class LoginResponse(val id: UUID, val email: String, val token: String, val tokenFirebaseAuth: String)
