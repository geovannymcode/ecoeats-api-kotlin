package com.geovannycode.ecoeats.dish.application

import java.math.BigDecimal

internal data class DishChanges(
    val name: String,
    val description: String,
    val price: BigDecimal,
    val carbohydrates: Int,
    val proteins: Int,
    val rating: BigDecimal,
    val ingredients: String,
    val flagHeader: Boolean = false,
)
