package com.geovannycode.ecoeats.dish.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.UUID

internal data class DishResponse(
    @field:Schema(
        description = "Identificador UUID del recurso",
        example = "0199c394-3880-7000-8000-000000000001",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val id: UUID,
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
    @field:Schema(
        description = "URL HTTP/HTTPS o URL prefirmada temporal (IMAGE_URL_TTL). Volver a pedir al listar; no persistir la URL",
        example = "https://storage.example.com/ecoeats/dishes/example.jpg?X-Amz-Expires=86400",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val image: String,
    @JsonProperty("thumbails")
    @field:Schema(
        description = "thumbails está escrito así a propósito por compatibilidad. URL temporal: volver a pedir al listar y no persistir",
        example = "https://storage.example.com/ecoeats/dishes/example-thumb.jpg?X-Amz-Expires=86400",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val thumbnail: String,
    @field:Schema(
        description = "Precio como número JSON, mayor o igual a cero; hasta 8 enteros y 2 decimales",
        example = "28000.00",
        type = "number",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val price: BigDecimal,
    @field:Schema(description = "Carbohidratos como entero no negativo", example = "85", requiredMode = Schema.RequiredMode.REQUIRED)
    val carbohydrates: Int,
    @field:Schema(description = "Proteínas como entero no negativo", example = "55", requiredMode = Schema.RequiredMode.REQUIRED)
    val proteins: Int,
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
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val flagHeader: Boolean,
)
