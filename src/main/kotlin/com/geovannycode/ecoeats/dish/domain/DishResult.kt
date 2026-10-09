package com.geovannycode.ecoeats.dish.domain

internal sealed interface DishResult {
    data class Found(
        val dish: DishEntity,
    ) : DishResult

    data object NotFound : DishResult
}

internal sealed interface DishDeletionResult {
    data object Deleted : DishDeletionResult

    data object NotFound : DishDeletionResult
}
