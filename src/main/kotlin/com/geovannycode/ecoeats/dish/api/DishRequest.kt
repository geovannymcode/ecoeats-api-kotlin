package com.geovannycode.ecoeats.dish.api

import com.geovannycode.ecoeats.dish.application.DishChanges
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

internal data class DishRequest(
    @field:NotBlank(message = "El nombre es obligatorio")
    @field:Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    @field:Schema(
        description = "Nombre del plato, de 1 a 150 caracteres",
        example = "Bandeja Paisa",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val name: String,
    @field:Schema(
        description = "Descripción del plato",
        example =
            "El plato más emblemático de la región antioqueña. Una generosa bandeja con frijoles " +
                "rojos, arroz blanco, chicharrón crujiente, carne molida, chorizo, huevo frito, plátano " +
                "maduro, arepa y aguacate.",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val description: String,
    @field:DecimalMin(value = "0", message = "El precio debe ser mayor o igual a 0")
    @field:Digits(integer = 8, fraction = 2, message = "El precio admite hasta 8 enteros y 2 decimales")
    @field:Schema(
        description = "Precio como número JSON, mayor o igual a cero; hasta 8 enteros y 2 decimales",
        example = "28000.00",
        type = "number",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val price: BigDecimal,
    @field:Min(value = 0, message = "Los carbohidratos deben ser mayores o iguales a 0")
    @field:Schema(description = "Carbohidratos como entero no negativo", example = "85", requiredMode = Schema.RequiredMode.REQUIRED)
    val carbohydrates: Int,
    @field:Min(value = 0, message = "Las proteínas deben ser mayores o iguales a 0")
    @field:Schema(description = "Proteínas como entero no negativo", example = "55", requiredMode = Schema.RequiredMode.REQUIRED)
    val proteins: Int,
    @field:DecimalMin(value = "0", message = "La calificación debe ser mayor o igual a 0")
    @field:DecimalMax(value = "5", message = "La calificación debe ser menor o igual a 5")
    @field:Digits(integer = 2, fraction = 1, message = "La calificación admite hasta un decimal")
    @field:Schema(
        description = "Calificación como número JSON, entre 0 y 5; hasta un decimal",
        example = "4.9",
        type = "number",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val rating: BigDecimal,
    @field:Schema(
        description = "Ingredientes como texto",
        example = "Frijoles rojos, arroz blanco, chicharrón, carne molida, chorizo, huevo frito, plátano maduro, arepa, aguacate",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val ingredients: String,
    @field:Schema(
        description = "Indica si el plato se muestra como destacado",
        example = "false",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
        defaultValue = "false",
    )
    val flagHeader: Boolean = false,
)

internal fun DishRequest.toChanges(): DishChanges =
    DishChanges(name, description, price, carbohydrates, proteins, rating, ingredients, flagHeader)
