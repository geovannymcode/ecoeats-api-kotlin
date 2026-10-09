package com.geovannycode.ecoeats.dish.api

import com.geovannycode.ecoeats.dish.application.DishService
import com.geovannycode.ecoeats.dish.domain.DishDeletionResult
import com.geovannycode.ecoeats.dish.domain.DishResult
import com.geovannycode.ecoeats.image.application.ImageUrlResolver
import com.geovannycode.ecoeats.shared.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/dish")
internal class DishController(
    private val dishes: DishService,
    private val images: ImageUrlResolver,
) {
    @GetMapping
    fun list(): ApiResponse<List<DishResponse>> = ApiResponse.ok(dishes.list().map { it.toResponse(images) })

    @GetMapping("/{id}")
    fun find(
        @PathVariable id: UUID,
    ): ResponseEntity<*> = dishes.find(id).toHttpResponse("OK", images)

    @PostMapping
    fun create(
        @Valid @RequestBody request: DishRequest,
    ): ResponseEntity<ApiResponse<DishResponse>> =
        ResponseEntity.status(201).body(ApiResponse.ok(dishes.create(request.toChanges()).toResponse(images), "Plato creado correctamente"))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: DishRequest,
    ): ResponseEntity<*> = dishes.update(id, request.toChanges()).toHttpResponse("Plato actualizado correctamente", images)

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        when (dishes.delete(id)) {
            DishDeletionResult.Deleted -> ResponseEntity.ok(ApiResponse.ok<Nothing>(null, "Plato eliminado correctamente"))
            DishDeletionResult.NotFound -> dishNotFound()
        }
}

private fun DishResult.toHttpResponse(
    message: String,
    images: ImageUrlResolver,
): ResponseEntity<*> =
    when (this) {
        is DishResult.Found -> ResponseEntity.ok(ApiResponse.ok(dish.toResponse(images), message))
        DishResult.NotFound -> dishNotFound()
    }

private fun dishNotFound(): ResponseEntity<ApiResponse<Nothing>> = ResponseEntity.status(404).body(ApiResponse.fail("Plato no encontrado"))
