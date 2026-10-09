package com.geovannycode.ecoeats.image.application

internal interface ImageUrlResolver {
    fun resolve(stored: String): String
}
