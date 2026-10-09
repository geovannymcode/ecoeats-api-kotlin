package com.geovannycode.ecoeats.auth.api

import com.geovannycode.ecoeats.auth.application.AuthService
import com.geovannycode.ecoeats.auth.domain.LoginResult
import com.geovannycode.ecoeats.auth.domain.RegistrationResult
import com.geovannycode.ecoeats.auth.domain.UserEntity
import com.geovannycode.ecoeats.shared.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/securities")
internal class AuthController(private val auth: AuthService) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<*> =
        when (val result = auth.register(request.numeroDocumento, request.nombres, request.apellidos, request.correo, request.password)) {
            is RegistrationResult.Registered -> ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(RegisterResponse(result.user.toRegisteredUser()), "Usuario registrado correctamente"))
            RegistrationResult.EmailTaken -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("El email ya está registrado"))
            RegistrationResult.DocumentTaken -> ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("El número de documento ya está registrado"))
        }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<*> = when (val result = auth.login(request.email, request.password)) {
        is LoginResult.Authenticated -> result.response.let {
            ResponseEntity.ok(ApiResponse.ok(LoginResponse(it.id, it.email, it.token, it.tokenFirebaseAuth), "Login exitoso"))
        }
        LoginResult.InvalidCredentials -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("Credenciales inválidas"))
    }
}

private fun UserEntity.toRegisteredUser(): RegisteredUser =
    RegisteredUser(checkNotNull(id), numeroDocumento, nombres, apellidos, email)
