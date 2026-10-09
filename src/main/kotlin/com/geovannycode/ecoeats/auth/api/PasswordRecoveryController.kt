package com.geovannycode.ecoeats.auth.api

import com.geovannycode.ecoeats.auth.application.PasswordRecoveryService
import com.geovannycode.ecoeats.auth.domain.ForgotPasswordResult
import com.geovannycode.ecoeats.auth.domain.PasswordResetResult
import com.geovannycode.ecoeats.shared.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/securities")
internal class PasswordRecoveryController(private val recovery: PasswordRecoveryService) {
    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<*> =
        when (recovery.forgotPassword(request.email)) {
            ForgotPasswordResult.Sent -> ResponseEntity.ok(ApiResponse.ok<Nothing>(null, "Código de recuperación enviado"))
            ForgotPasswordResult.UserNotFound -> ResponseEntity.status(404).body(ApiResponse.fail("No se encontró un usuario con ese email"))
        }

    @PostMapping("/verify-otp")
    fun verifyOtp(@Valid @RequestBody request: VerifyOtpRequest): ResponseEntity<ApiResponse<VerifyOtpResponse>> {
        val valid = recovery.verifyOtp(request.email, request.otp)
        return ResponseEntity.ok(ApiResponse.ok(VerifyOtpResponse(valid), if (valid) "OTP válido" else "OTP inválido o expirado"))
    }

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<*> =
        when (recovery.resetPassword(request.email, request.otp, request.newPassword)) {
            PasswordResetResult.Reset -> ResponseEntity.ok(ApiResponse.ok<Nothing>(null, "Contraseña restablecida exitosamente"))
            PasswordResetResult.InvalidOtp -> ResponseEntity.badRequest().body(ApiResponse.fail("OTP inválido o expirado"))
        }
}
