package com.geovannycode.ecoeats.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "password_reset_tokens")
internal class PasswordResetTokenEntity(
    @field:Column(name = "user_id", nullable = false)
    val userId: UUID,
    @field:Column(name = "otp_hash", nullable = false, length = 64)
    val otpHash: String,
    @field:Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @field:Column(nullable = false)
    var attempts: Int = 0,
    @field:Column(nullable = false)
    var used: Boolean = false,
) {
    @field:Id
    @field:GeneratedValue
    @field:UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    val id: UUID? = null

    @field:CreationTimestamp
    @field:Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    final override fun equals(other: Any?): Boolean =
        this === other || (other is PasswordResetTokenEntity && id != null && id == other.id)

    final override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "PasswordResetTokenEntity(id=$id, userId=$userId, used=$used)"
}
