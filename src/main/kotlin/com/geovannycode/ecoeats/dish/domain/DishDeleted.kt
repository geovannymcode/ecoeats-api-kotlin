package com.geovannycode.ecoeats.dish.domain

import java.util.UUID

// Future bucket cleanup listeners should run AFTER_COMMIT.
internal data class DishDeleted(
    val id: UUID,
    val image: String,
    val thumbnail: String,
)
