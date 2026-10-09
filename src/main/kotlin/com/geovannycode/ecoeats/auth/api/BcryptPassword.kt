package com.geovannycode.ecoeats.auth.api

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [BcryptPasswordValidator::class])
internal annotation class BcryptPassword(
    val message: String = "La contraseña no puede superar 72 bytes UTF-8",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
