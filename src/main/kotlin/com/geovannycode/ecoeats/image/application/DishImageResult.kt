package com.geovannycode.ecoeats.image.application

import com.geovannycode.ecoeats.dish.domain.DishEntity

internal sealed interface DishImageResult {
    data class Uploaded(
        val dish: DishEntity,
    ) : DishImageResult

    data object DishNotFound : DishImageResult

    data object UnsupportedFormat : DishImageResult

    data object CorruptImage : DishImageResult
}

internal data class DishImagesReplaced(
    val image: String,
    val thumbnail: String,
)
