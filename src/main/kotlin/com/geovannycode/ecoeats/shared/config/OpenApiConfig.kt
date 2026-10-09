package com.geovannycode.ecoeats.shared.config

import com.geovannycode.ecoeats.auth.api.ForgotPasswordRequest
import com.geovannycode.ecoeats.auth.api.LoginRequest
import com.geovannycode.ecoeats.auth.api.LoginResponse
import com.geovannycode.ecoeats.auth.api.RegisterRequest
import com.geovannycode.ecoeats.auth.api.RegisterResponse
import com.geovannycode.ecoeats.auth.api.RegisteredUser
import com.geovannycode.ecoeats.auth.api.ResetPasswordRequest
import com.geovannycode.ecoeats.auth.api.VerifyOtpRequest
import com.geovannycode.ecoeats.auth.api.VerifyOtpResponse
import com.geovannycode.ecoeats.dish.api.DishRequest
import com.geovannycode.ecoeats.dish.api.DishResponse
import com.geovannycode.ecoeats.shared.api.FieldError
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.BooleanSchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.DateTimeSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import io.swagger.v3.oas.models.responses.ApiResponse as OpenApiResponse

@Configuration(proxyBeanMethods = false)
internal class OpenApiConfig(
    private val build: BuildProperties,
    @param:Value("\${ecoeats.public-url:}") private val publicUrl: String,
) {
    @Bean
    fun ecoEatsOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("EcoEats API")
                    .version(build.version)
                    .description("Comida colombiana saludable: autenticación, catálogo e imágenes para clientes móviles y web.")
                    .contact(Contact().name("Geovanny Mendoza"))
                    .license(License().name("MIT").url("https://opensource.org/licenses/MIT")),
            ).servers(
                buildList {
                    add(Server().url("http://localhost:8080").description("Local"))
                    if (publicUrl.isNotBlank()) add(Server().url(publicUrl.trimEnd('/')).description("Producción (PUBLIC_URL)"))
                },
            ).tags(
                listOf(
                    Tag().name("Auth").description("Registro, login y recuperación de contraseña; rutas públicas."),
                    Tag().name("Platos").description("Lectura con JWT y CRUD exclusivo para ADMIN."),
                    Tag().name("Imágenes").description("Subida y borrado exclusivo para ADMIN; URLs prefirmadas temporales."),
                    Tag().name("Health").description("Estado público de la API; formato de Spring Boot Actuator."),
                ),
            ).components(
                Components().addSecuritySchemes(
                    "bearerAuth",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )

    @Bean
    fun apiContractDocumentation(): OpenApiCustomizer =
        OpenApiCustomizer { api ->
            val components = api.components
            listOf(
                RegisterRequest::class.java,
                RegisteredUser::class.java,
                RegisterResponse::class.java,
                LoginRequest::class.java,
                LoginResponse::class.java,
                ForgotPasswordRequest::class.java,
                VerifyOtpRequest::class.java,
                VerifyOtpResponse::class.java,
                ResetPasswordRequest::class.java,
                DishRequest::class.java,
                DishResponse::class.java,
                FieldError::class.java,
            ).forEach { type ->
                ModelConverters.getInstance().read(type).forEach { (name, schema) -> components.addSchemas(name, schema) }
            }
            components.addSchemas("ApiResponseRegister", envelope(reference("RegisterResponse")))
            components.addSchemas("ApiResponseLogin", envelope(reference("LoginResponse")))
            components.addSchemas("ApiResponseDish", envelope(reference("DishResponse")))
            components.addSchemas("ApiResponseDishList", envelope(ArraySchema().items(reference("DishResponse"))))
            components.addSchemas("ApiResponseVerifyOtp", envelope(reference("VerifyOtpResponse")))
            components.addSchemas("ApiResponseEmpty", envelope(nullData()))
            components.schemas["LoginResponse"]
                ?.properties
                ?.get("tokenFirebaseAuth")
                ?.example = ""
            components.addSchemas(
                "ApiErrorResponse",
                ObjectSchema().apply {
                    addProperty("success", BooleanSchema().example(false)._enum(listOf(false)))
                    addProperty("message", StringSchema().example("Plato no encontrado"))
                    addProperty("data", nullData())
                    addProperty("timestamp", DateTimeSchema().example("2026-10-09T12:00:00Z"))
                    addProperty(
                        "errors",
                        ArraySchema()
                            .items(reference("FieldError"))
                            .description("Presente únicamente cuando fallan validaciones por campo."),
                    )
                    required = listOf("success", "message", "data", "timestamp")
                },
            )
            components.addSchemas(
                "ApiHealthResponse",
                ObjectSchema().apply {
                    addProperty("status", StringSchema().example("UP")._enum(listOf("UP", "DOWN", "OUT_OF_SERVICE", "UNKNOWN")))
                    required = listOf("status")
                },
            )
            // Health is an Actuator endpoint rather than a business controller.
            api.paths.addPathItem("/health", PathItem().get(Operation().operationId("health")))
            api.paths.forEach { (path, item) ->
                item.readOperationsMap().forEach { (method, operation) ->
                    val doc = operations["${method.name} $path"]
                    if (doc != null) document(operation, doc)
                }
            }
            val upload = checkNotNull(api.paths["/api/dish/{id}/image"]).post
            upload.requestBody =
                io.swagger.v3.oas.models.parameters
                    .RequestBody()
                    .required(true)
                    .description("Archivo JPEG, PNG o WebP de hasta 5 MB. El nombre de la parte debe ser file.")
                    .content(
                        Content().addMediaType(
                            "multipart/form-data",
                            MediaType().schema(
                                ObjectSchema().apply {
                                    addProperty("file", StringSchema().format("binary").description("Imagen de hasta 5 MB"))
                                    required = listOf("file")
                                },
                            ),
                        ),
                    )
            // Remove inferred generic envelopes that do not represent the HTTP contract.
            components.schemas.keys.removeIf { it.startsWith("ApiResponse") && it !in WRAPPERS }
        }

    private fun document(
        operation: Operation,
        doc: OperationDoc,
    ) {
        operation.summary = doc.summary
        operation.description = doc.description
        operation.tags = listOf(doc.tag)
        operation.security =
            if (doc.tag in setOf("Platos", "Imágenes")) listOf(SecurityRequirement().addList("bearerAuth")) else emptyList()
        operation.responses = ApiResponses()
        val codes = doc.codes + if (doc.tag == "Health") emptyList() else listOf(429)
        val bodyCodes = if (doc.tag == "Auth" || doc.summary in setOf("Crear plato", "Actualizar plato")) listOf(413) else emptyList()
        (codes + bodyCodes).distinct().sorted().forEach { code ->
            val successful = code in 200..299
            val schema =
                if (doc.tag == "Health") {
                    "ApiHealthResponse"
                } else if (successful) {
                    doc.schema
                } else {
                    "ApiErrorResponse"
                }
            val messages = messagesFor(doc, code)
            val media = MediaType().schema(reference(schema))
            messages.forEachIndexed { index, message ->
                val value =
                    if (doc.tag == "Health") {
                        mapOf("status" to if (successful) "UP" else "DOWN")
                    } else if (successful) {
                        mapOf(
                            "success" to true,
                            "message" to message,
                            "data" to
                                if (message == "OTP inválido o expirado") mapOf("valid" to false) else successData(doc),
                        )
                    } else {
                        errorExample(message, doc)
                    }
                media.addExamples("example${index + 1}", Example().summary(message).value(value))
            }
            operation.responses.addApiResponse(
                code.toString(),
                OpenApiResponse()
                    .description(messages.joinToString(" / "))
                    .addHeaderObject(
                        "X-Request-Id",
                        io.swagger.v3.oas.models.headers
                            .Header()
                            .description("Identificador de solicitud")
                            .schema(StringSchema()),
                    ).apply {
                        if (code ==
                            429
                        ) {
                            addHeaderObject(
                                "Retry-After",
                                io.swagger.v3.oas.models.headers
                                    .Header()
                                    .description(
                                        "Segundos hasta el siguiente intento",
                                    ).schema(
                                        io.swagger.v3.oas.models.media
                                            .IntegerSchema()
                                            .minimum(java.math.BigDecimal.ONE),
                                    ),
                            )
                        }
                    }.content(Content().addMediaType("application/json", media)),
            )
        }
    }
}

private data class OperationDoc(
    val tag: String,
    val summary: String,
    val description: String,
    val schema: String,
    val successMessage: String,
    val codes: List<Int>,
)

private val operations =
    mapOf(
        "POST /api/securities/register" to
            OperationDoc(
                "Auth",
                "Registrar usuario",
                "Público. Crea un usuario USER. correo se normaliza; email y numero_documento son únicos. No devuelve un token.",
                "ApiResponseRegister",
                "Usuario registrado correctamente",
                listOf(201, 400, 409, 415, 500),
            ),
        "POST /api/securities/login" to
            OperationDoc(
                "Auth",
                "Iniciar sesión",
                "Público. Devuelve JWT HS256 con vigencia JWT_EXPIRATION (24h por defecto). No hay refresh " +
                    "token; ante 401 iniciar sesión de nuevo.",
                "ApiResponseLogin",
                "Login exitoso",
                listOf(200, 400, 401, 415, 500),
            ),
        "POST /api/securities/forgot-password" to
            OperationDoc(
                "Auth",
                "Solicitar código de recuperación",
                "Público. Envía por email un OTP de seis dígitos válido durante 15 minutos e invalida los " +
                    "anteriores. Email inexistente: 404; fallo de entrega: 503.",
                "ApiResponseEmpty",
                "Código de recuperación enviado",
                listOf(200, 400, 404, 415, 500, 503),
            ),
        "POST /api/securities/verify-otp" to
            OperationDoc(
                "Auth",
                "Verificar código OTP",
                "Público. HTTP 200 tanto para valid=true como false. Cinco intentos fallidos inutilizan el " +
                    "código. Verificar no consume el OTP.",
                "ApiResponseVerifyOtp",
                "OTP válido",
                listOf(200, 400, 415, 500),
            ),
        "POST /api/securities/reset-password" to
            OperationDoc(
                "Auth",
                "Restablecer contraseña",
                "Público. Valida y consume el OTP; la nueva contraseña debe tener al menos seis caracteres y como máximo 72 bytes UTF-8.",
                "ApiResponseEmpty",
                "Contraseña restablecida exitosamente",
                listOf(200, 400, 415, 500),
            ),
        "GET /api/dish" to
            OperationDoc(
                "Platos",
                "Listar platos",
                "JWT, cualquier rol. Lista ordenada por name. image y thumbails pueden ser URLs " +
                    "temporales: volver a pedirlas al listar, no persistirlas. El seed contiene 12 platos.",
                "ApiResponseDishList",
                "OK",
                listOf(200, 401, 500),
            ),
        "GET /api/dish/{id}" to
            OperationDoc(
                "Platos",
                "Consultar plato",
                "JWT, cualquier rol. id debe ser un UUID. thumbails conserva intencionalmente el typo del " +
                    "contrato móvil; price y rating son números JSON.",
                "ApiResponseDish",
                "OK",
                listOf(200, 400, 401, 404, 500),
            ),
        "POST /api/dish" to
            OperationDoc(
                "Platos",
                "Crear plato",
                "JWT, solo ADMIN. No admite campos de imagen; subirla mediante el endpoint multipart " +
                    "después de crear el plato. flagHeader por defecto false.",
                "ApiResponseDish",
                "Plato creado correctamente",
                listOf(201, 400, 401, 403, 415, 500),
            ),
        "PUT /api/dish/{id}" to
            OperationDoc(
                "Platos",
                "Actualizar plato",
                "JWT, solo ADMIN. Reemplaza todos los campos editables del request y conserva las " +
                    "imágenes. No es una actualización parcial.",
                "ApiResponseDish",
                "Plato actualizado correctamente",
                listOf(200, 400, 401, 403, 404, 415, 500),
            ),
        "DELETE /api/dish/{id}" to
            OperationDoc(
                "Platos",
                "Eliminar plato",
                "JWT, solo ADMIN. Devuelve data:null. Las imágenes del bucket se borran después del commit; no borra URLs externas.",
                "ApiResponseEmpty",
                "Plato eliminado correctamente",
                listOf(200, 400, 401, 403, 404, 500),
            ),
        "POST /api/dish/{id}/image" to
            OperationDoc(
                "Imágenes",
                "Subir imagen de plato",
                "JWT, solo ADMIN. Multipart con parte file (máximo 5 MB, petición 6 MB). Valida magic " +
                    "bytes JPEG/PNG/WebP, genera JPEG de máximo 1200 px y miniatura de máximo 300 px. URLs " +
                    "firmadas según IMAGE_URL_TTL (24h por defecto). Reemplaza y limpia imágenes anteriores.",
                "ApiResponseDish",
                "Imagen actualizada correctamente",
                listOf(200, 400, 401, 403, 404, 413, 415, 422, 500),
            ),
        "DELETE /api/dish/{id}/image" to
            OperationDoc(
                "Imágenes",
                "Quitar imagen de plato",
                "JWT, solo ADMIN. Conserva el plato, vacía image y thumbails y borra objetos propios del bucket después del commit.",
                "ApiResponseDish",
                "Imagen eliminada correctamente",
                listOf(200, 400, 401, 403, 404, 500),
            ),
        "GET /health" to
            OperationDoc(
                "Health",
                "Consultar salud",
                "Público. Formato de Spring Boot Actuator: {status:UP}. No utiliza el envelope de negocio. " +
                    "503 si está DOWN o OUT_OF_SERVICE.",
                "ApiHealthResponse",
                "Servicio disponible",
                listOf(200, 503),
            ),
    )

private val WRAPPERS =
    setOf("ApiResponseRegister", "ApiResponseLogin", "ApiResponseDish", "ApiResponseDishList", "ApiResponseVerifyOtp", "ApiResponseEmpty")
private const val EXAMPLE_ID = "0199c394-3880-7000-8000-000000000001"
private val exampleUser =
    mapOf(
        "id" to EXAMPLE_ID,
        "numero_documento" to "1032456789",
        "nombres" to "Geovanny",
        "apellidos" to "Mendoza",
        "correo" to "geovanny@example.com",
    )
private val exampleDish =
    mapOf(
        "id" to EXAMPLE_ID,
        "name" to "Bandeja Paisa",
        "description" to
            "El plato más emblemático de la región antioqueña. Una generosa bandeja con frijoles " +
            "rojos, arroz blanco, chicharrón crujiente, carne molida, chorizo, huevo frito, plátano " +
            "maduro, arepa y aguacate.",
        "image" to "https://images.unsplash.com/photo-1599655722738-ae7a40b0b994?w=600",
        "thumbails" to "https://images.unsplash.com/photo-1599655722738-ae7a40b0b994?w=200",
        "price" to 28000.00,
        "carbohydrates" to 85,
        "proteins" to 55,
        "rating" to 4.9,
        "ingredients" to
            "Frijoles rojos, arroz blanco, chicharrón, carne molida, chorizo, huevo frito, plátano maduro, arepa, aguacate",
        "flagHeader" to true,
    )

private fun successData(doc: OperationDoc): Any? =
    when (doc.schema) {
        "ApiResponseRegister" -> mapOf("user" to exampleUser)
        "ApiResponseLogin" ->
            mapOf(
                "id" to EXAMPLE_ID,
                "email" to "geovanny@example.com",
                "token" to "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJleGFtcGxlIn0.example-signature",
                "tokenFirebaseAuth" to "",
            )
        "ApiResponseDish" ->
            when (doc.summary) {
                "Crear plato", "Quitar imagen de plato" -> exampleDish + mapOf("image" to "", "thumbails" to "")
                "Subir imagen de plato" ->
                    exampleDish +
                        mapOf(
                            "image" to "https://storage.example.com/ecoeats/dishes/example.jpg?X-Amz-Expires=86400",
                            "thumbails" to "https://storage.example.com/ecoeats/dishes/example-thumb.jpg?X-Amz-Expires=86400",
                        )
                else -> exampleDish
            }
        "ApiResponseDishList" -> listOf(exampleDish)
        "ApiResponseVerifyOtp" -> mapOf("valid" to true)
        else -> null
    }

private fun messagesFor(
    doc: OperationDoc,
    code: Int,
): List<String> =
    when {
        code == 200 && doc.schema == "ApiResponseVerifyOtp" -> listOf("OTP válido", "OTP inválido o expirado")
        code in 200..299 -> listOf(doc.successMessage)
        doc.tag == "Health" -> listOf("Servicio no disponible")
        code == 400 && doc.summary == "Restablecer contraseña" ->
            listOf(
                "Datos de entrada inválidos",
                "OTP inválido o expirado",
                "Cuerpo de la petición inválido",
            )
        code == 400 ->
            when (doc.summary) {
                "Consultar plato", "Eliminar plato", "Quitar imagen de plato" -> listOf("Parámetro de la petición inválido")
                "Subir imagen de plato" -> listOf("Parámetro de la petición inválido", "Falta el archivo de imagen")
                "Actualizar plato" ->
                    listOf(
                        "Datos de entrada inválidos",
                        "Parámetro de la petición inválido",
                        "Cuerpo de la petición inválido",
                    )
                else -> listOf("Datos de entrada inválidos", "Cuerpo de la petición inválido")
            }
        code == 401 -> if (doc.tag == "Auth") listOf("Credenciales inválidas") else listOf("No autorizado", "Token inválido")
        code == 403 -> listOf("Acceso denegado")
        code == 404 -> listOf(if (doc.tag == "Auth") "No se encontró un usuario con ese email" else "Plato no encontrado")
        code == 409 -> listOf("El email ya está registrado", "El número de documento ya está registrado")
        code == 413 ->
            listOf(
                if (doc.tag ==
                    "Imágenes"
                ) {
                    "La imagen supera el tamaño máximo de 5 MB"
                } else {
                    "El cuerpo JSON supera el tamaño máximo de 1 MB"
                },
            )
        code == 429 -> listOf("Demasiadas solicitudes, intenta más tarde")
        code == 415 ->
            if (doc.tag ==
                "Imágenes"
            ) {
                listOf("Formato no soportado. Usa JPEG, PNG o WebP", "Tipo de contenido no soportado")
            } else {
                listOf("Tipo de contenido no soportado")
            }
        code == 422 -> listOf("La imagen está dañada")
        code == 503 -> listOf("No fue posible enviar el correo, intenta de nuevo")
        else -> listOf("Error interno del servidor")
    }

private fun errorExample(
    message: String,
    doc: OperationDoc,
): Map<String, Any?> =
    buildMap {
        put("success", false)
        put("message", message)
        put("data", null)
        put("timestamp", "2026-10-09T12:00:00Z")
        if (message == "Datos de entrada inválidos") {
            val field =
                when (doc.summary) {
                    "Registrar usuario" -> "correo"
                    "Verificar código OTP", "Restablecer contraseña" -> "otp"
                    "Crear plato", "Actualizar plato" -> "name"
                    else -> "email"
                }
            val fieldMessage =
                when (field) {
                    "correo" -> "El correo debe ser válido"
                    "otp" -> "El OTP debe tener exactamente 6 dígitos"
                    "name" -> "El nombre es obligatorio"
                    else -> "El email debe ser válido"
                }
            put("errors", listOf(mapOf("field" to field, "message" to fieldMessage)))
        }
    }

private fun reference(name: String): Schema<Any> = Schema<Any>().`$ref`("#/components/schemas/$name")

private fun nullData(): Schema<Any> = Schema<Any>().type("object").nullable(true)._enum(listOf(null))

private fun envelope(data: Schema<*>): ObjectSchema =
    ObjectSchema().apply {
        addProperty("success", BooleanSchema().example(true)._enum(listOf(true)))
        addProperty("message", StringSchema().example("OK"))
        addProperty("data", data)
        required = listOf("success", "message", "data")
    }
