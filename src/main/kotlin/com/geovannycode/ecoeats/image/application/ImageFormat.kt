package com.geovannycode.ecoeats.image.application

internal enum class ImageFormat {
    JPEG,
    PNG,
    WEBP,
    ;

    companion object {
        fun detect(bytes: ByteArray): ImageFormat? =
            when {
                bytes.startsWithBytes(0xff, 0xd8, 0xff) -> JPEG
                bytes.startsWithBytes(0x89, 0x50, 0x4e, 0x47) -> PNG
                bytes.size >= 12 &&
                    bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
                    bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray(Charsets.US_ASCII)) -> WEBP
                else -> null
            }
    }
}

private fun ByteArray.startsWithBytes(vararg prefix: Int): Boolean =
    size >= prefix.size && prefix.indices.all { this[it].toInt() and 0xff == prefix[it] }
