package com.geovannycode.ecoeats.dish.api

import com.geovannycode.ecoeats.dish.domain.DishEntity
import com.geovannycode.ecoeats.image.application.ImageUrlResolver

internal fun DishEntity.toResponse(imageUrlResolver: ImageUrlResolver): DishResponse =
    DishResponse(
        checkNotNull(id),
        name,
        description,
        imageUrlResolver.resolve(image),
        imageUrlResolver.resolve(thumbnail),
        price,
        carbohydrates,
        proteins,
        rating,
        ingredients,
        flagHeader,
    )
