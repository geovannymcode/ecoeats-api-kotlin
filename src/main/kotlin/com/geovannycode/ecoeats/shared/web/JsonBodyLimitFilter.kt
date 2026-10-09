package com.geovannycode.ecoeats.shared.web

import com.geovannycode.ecoeats.shared.security.SecurityErrorWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

@Component
internal class JsonBodyLimitFilter(
    private val errors: SecurityErrorWriter,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.contentType?.substringBefore(';')?.trim()?.let {
            it != "application/json" && !it.endsWith("+json")
        } ?: true

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (request.contentLengthLong > MAX_BYTES) {
            reject(response)
            return
        }
        val bytes = request.inputStream.readNBytes(MAX_BYTES + 1)
        if (bytes.size > MAX_BYTES) {
            reject(response)
            return
        }
        chain.doFilter(BufferedJsonRequest(request, bytes), response)
    }

    private fun reject(response: HttpServletResponse) = errors.write(response, 413, "El cuerpo JSON supera el tamaño máximo de 1 MB")

    companion object {
        private const val MAX_BYTES = 1024 * 1024
    }
}

private class BufferedJsonRequest(
    request: HttpServletRequest,
    private val bytes: ByteArray,
) : HttpServletRequestWrapper(request) {
    override fun getInputStream(): ServletInputStream {
        val input = ByteArrayInputStream(bytes)
        return object : ServletInputStream() {
            override fun read(): Int = input.read()

            override fun read(
                buffer: ByteArray,
                offset: Int,
                length: Int,
            ): Int = input.read(buffer, offset, length)

            override fun isFinished(): Boolean = input.available() == 0

            override fun isReady(): Boolean = true

            override fun setReadListener(listener: ReadListener) {
                // Spring MVC uses blocking reads; still honor the servlet callback contract.
                try {
                    if (isFinished) listener.onAllDataRead() else listener.onDataAvailable()
                } catch (
                    failure: Exception,
                ) {
                    listener.onError(failure)
                }
            }
        }
    }

    override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

    override fun getContentLength(): Int = bytes.size

    override fun getContentLengthLong(): Long = bytes.size.toLong()
}
