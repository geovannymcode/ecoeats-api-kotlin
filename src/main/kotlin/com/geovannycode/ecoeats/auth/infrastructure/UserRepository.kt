package com.geovannycode.ecoeats.auth.infrastructure

import com.geovannycode.ecoeats.auth.domain.UserEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface UserRepository : JpaRepository<UserEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserEntity user where lower(user.email) = lower(:email)")
    fun findByEmailForUpdate(@Param("email") email: String): UserEntity?

    fun findByEmailIgnoreCase(email: String): UserEntity?
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun existsByNumeroDocumento(numeroDocumento: String): Boolean
}
