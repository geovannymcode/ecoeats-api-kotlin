package com.geovannycode.ecoeats.auth.api

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

internal class BcryptPasswordValidator : ConstraintValidator<BcryptPassword, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || value.toByteArray(Charsets.UTF_8).size <= 72
}
