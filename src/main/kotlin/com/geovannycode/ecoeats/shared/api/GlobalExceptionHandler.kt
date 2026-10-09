package com.geovannycode.ecoeats.shared.api

import com.geovannycode.ecoeats.notification.EmailDeliveryException
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
internal class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(Exception::class)
    fun handle(exception: Exception): ResponseEntity<ApiResponse<Nothing>> = when (exception) {
        is MethodArgumentNotValidException -> ApiResponse.fail(
            "Datos de entrada inválidos",
            exception.bindingResult.fieldErrors.map {
                FieldError(exception.bindingResult.target.jsonFieldName(it.field), it.defaultMessage ?: "Valor inválido")
            },
        ).withStatus(HttpStatus.BAD_REQUEST)
        is HandlerMethodValidationException -> ApiResponse.fail(
            "Datos de entrada inválidos",
            exception.parameterValidationResults.flatMap { result ->
                result.resolvableErrors.map {
                    FieldError(result.methodParameter.parameterName ?: "argument", it.defaultMessage ?: "Valor inválido")
                }
            },
        ).withStatus(HttpStatus.BAD_REQUEST)
        is EmailDeliveryException -> ApiResponse.fail("No fue posible enviar el correo, intenta de nuevo").withStatus(HttpStatus.SERVICE_UNAVAILABLE)
        is HttpMessageNotReadableException -> ApiResponse.fail("Cuerpo de la petición inválido").withStatus(HttpStatus.BAD_REQUEST)
        is MethodArgumentTypeMismatchException -> ApiResponse.fail("Parámetro de la petición inválido").withStatus(HttpStatus.BAD_REQUEST)
        is NoResourceFoundException -> ApiResponse.fail("Recurso no encontrado").withStatus(HttpStatus.NOT_FOUND)
        is HttpRequestMethodNotSupportedException -> ApiResponse.fail("Método HTTP no permitido").withStatus(HttpStatus.METHOD_NOT_ALLOWED)
        is AuthenticationException -> ApiResponse.fail("Credenciales inválidas").withStatus(HttpStatus.UNAUTHORIZED)
        is AccessDeniedException -> ApiResponse.fail("Acceso denegado").withStatus(HttpStatus.FORBIDDEN)
        else -> {
            // Exception messages and request values can contain credentials.
            logger.error("Unexpected request failure: {}\n{}", exception.javaClass.name,
                exception.stackTrace.joinToString("\n") { "at $it" })
            ApiResponse.fail("Error interno del servidor").withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }
}

private fun ApiResponse<Nothing>.withStatus(status: HttpStatus): ResponseEntity<ApiResponse<Nothing>> =
    ResponseEntity.status(status).body(this)

private fun Any?.jsonFieldName(field: String): String =
    this?.javaClass?.declaredFields?.firstOrNull { it.name == field }
        ?.getAnnotation(JsonProperty::class.java)?.value ?: field
