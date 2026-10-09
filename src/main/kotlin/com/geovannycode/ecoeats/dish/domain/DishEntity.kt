package com.geovannycode.ecoeats.dish.domain

import com.geovannycode.ecoeats.shared.domain.Auditable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "dishes")
internal class DishEntity(
    @field:Column(nullable = false, length = 150)
    var name: String,
    @field:Column(nullable = false, columnDefinition = "text")
    var description: String = "",
    @field:Column(nullable = false, length = 500)
    var image: String = "",
    @field:Column(nullable = false, length = 500)
    var thumbnail: String = "",
    @field:Column(nullable = false, precision = 10, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO,
    @field:Column(nullable = false)
    var carbohydrates: Int = 0,
    @field:Column(nullable = false)
    var proteins: Int = 0,
    @field:Column(nullable = false, precision = 3, scale = 1)
    var rating: BigDecimal = BigDecimal.ZERO,
    @field:Column(nullable = false, columnDefinition = "text")
    var ingredients: String = "",
    @field:Column(name = "flag_header", nullable = false)
    var flagHeader: Boolean = false,
) : Auditable() {
    @field:Id
    @field:GeneratedValue
    @field:UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    val id: UUID? = null

    final override fun equals(other: Any?): Boolean =
        this === other || (other is DishEntity && id != null && id == other.id)

    final override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "DishEntity(id=$id, name=$name)"
}
