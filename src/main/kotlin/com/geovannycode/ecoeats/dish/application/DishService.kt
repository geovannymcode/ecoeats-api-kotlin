package com.geovannycode.ecoeats.dish.application

import com.geovannycode.ecoeats.dish.domain.DishDeleted
import com.geovannycode.ecoeats.dish.domain.DishDeletionResult
import com.geovannycode.ecoeats.dish.domain.DishEntity
import com.geovannycode.ecoeats.dish.domain.DishResult
import com.geovannycode.ecoeats.dish.infrastructure.DishRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
internal class DishService(
    private val dishes: DishRepository,
    private val events: ApplicationEventPublisher,
) {
    fun list(): List<DishEntity> = dishes.findAllByOrderByNameAsc()

    fun find(id: UUID): DishResult = dishes.findByIdOrNull(id)?.let { DishResult.Found(it) } ?: DishResult.NotFound

    @Transactional
    fun create(changes: DishChanges): DishEntity =
        dishes.save(
            DishEntity(
                name = changes.name,
                description = changes.description,
                price = changes.price,
                carbohydrates = changes.carbohydrates,
                proteins = changes.proteins,
                rating = changes.rating,
                ingredients = changes.ingredients,
                flagHeader = changes.flagHeader,
            ),
        )

    @Transactional
    fun update(
        id: UUID,
        changes: DishChanges,
    ): DishResult {
        val dish = dishes.findByIdForUpdate(id) ?: return DishResult.NotFound
        dish.name = changes.name
        dish.description = changes.description
        dish.price = changes.price
        dish.carbohydrates = changes.carbohydrates
        dish.proteins = changes.proteins
        dish.rating = changes.rating
        dish.ingredients = changes.ingredients
        dish.flagHeader = changes.flagHeader
        return DishResult.Found(dishes.save(dish))
    }

    @Transactional
    fun delete(id: UUID): DishDeletionResult {
        val dish = dishes.findByIdForUpdate(id) ?: return DishDeletionResult.NotFound
        val event = DishDeleted(checkNotNull(dish.id), dish.image, dish.thumbnail)
        dishes.delete(dish)
        events.publishEvent(event)
        return DishDeletionResult.Deleted
    }
}
