package com.geovannycode.ecoeats.dish.api

import com.geovannycode.ecoeats.dish.domain.DishResult
import com.geovannycode.ecoeats.image.application.DishImageResult
import com.geovannycode.ecoeats.image.application.DishImageService
import com.geovannycode.ecoeats.image.application.ImageUrlResolver
import com.geovannycode.ecoeats.shared.api.ApiResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/dish/{id}/image")
internal class DishImageController(
    private val service: DishImageService,
    private val images: ImageUrlResolver,
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable id: UUID,
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<*> =
        when (val result = service.upload(id, file)) {
            is DishImageResult.Uploaded ->
                ResponseEntity.ok(
                    ApiResponse.ok(result.dish.toResponse(images), "Imagen actualizada correctamente"),
                )
            DishImageResult.DishNotFound -> ResponseEntity.status(404).body(ApiResponse.fail("Plato no encontrado"))
            DishImageResult.UnsupportedFormat ->
                ResponseEntity
                    .status(
                        415,
                    ).body(ApiResponse.fail("Formato no soportado. Usa JPEG, PNG o WebP"))
            DishImageResult.CorruptImage -> ResponseEntity.status(422).body(ApiResponse.fail("La imagen está dañada"))
        }

    @DeleteMapping
    fun remove(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        when (val result = service.remove(id)) {
            is DishResult.Found -> ResponseEntity.ok(ApiResponse.ok(result.dish.toResponse(images), "Imagen eliminada correctamente"))
            DishResult.NotFound -> ResponseEntity.status(404).body(ApiResponse.fail("Plato no encontrado"))
        }
}
