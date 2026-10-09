package com.geovannycode.ecoeats.dish.infrastructure

import com.geovannycode.ecoeats.dish.domain.DishEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface DishRepository : JpaRepository<DishEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dish from DishEntity dish where dish.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: UUID,
    ): DishEntity?

    fun findAllByOrderByNameAsc(): List<DishEntity>
}
