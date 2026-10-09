package com.geovannycode.ecoeats.auth.domain

import com.geovannycode.ecoeats.shared.domain.Auditable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

@Entity
@Table(name = "users")
internal class UserEntity(
    @field:Column(name = "numero_documento", nullable = false, length = 30, unique = true)
    val numeroDocumento: String,
    @field:Column(nullable = false, length = 100)
    val nombres: String,
    @field:Column(nullable = false, length = 100)
    val apellidos: String,
    @field:Column(nullable = false, length = 150, unique = true)
    val email: String,
    @field:Column(nullable = false, length = 100)
    var password: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false, length = 20)
    var role: Role = Role.USER,
    @field:Column(name = "token_firebase_auth", nullable = false, length = 500)
    var tokenFirebaseAuth: String = "",
) : Auditable() {
    @field:Id
    @field:GeneratedValue
    @field:UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    val id: UUID? = null

    final override fun equals(other: Any?): Boolean = this === other || (other is UserEntity && id != null && id == other.id)

    final override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "UserEntity(id=$id, role=$role)"
}
