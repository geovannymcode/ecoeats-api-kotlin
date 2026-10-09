package com.geovannycode.ecoeats.auth.infrastructure

import com.geovannycode.ecoeats.auth.domain.PasswordResetTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

internal interface PasswordResetTokenRepository : JpaRepository<PasswordResetTokenEntity, UUID> {
    fun findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(userId: UUID): PasswordResetTokenEntity?

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetTokenEntity token set token.used = true where token.userId = :userId and token.used = false")
    fun invalidateActiveTokens(@Param("userId") userId: UUID): Int
}
