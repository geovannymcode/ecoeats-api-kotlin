package com.geovannycode.ecoeats.image.application

internal interface ImageStorage {
    fun put(
        key: String,
        bytes: ByteArray,
        contentType: String,
    )

    fun delete(key: String)

    fun presignedGetUrl(key: String): String
}
