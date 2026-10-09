package com.geovannycode.ecoeats.dish.infrastructure

import com.geovannycode.ecoeats.dish.domain.DishEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface DishRepository : JpaRepository<DishEntity, UUID> {
    fun findAllByOrderByNameAsc(): List<DishEntity>
}
