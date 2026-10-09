package com.geovannycode.ecoeats.image.infrastructure

import com.geovannycode.ecoeats.image.application.ImageStorage
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

@Component
internal class S3ImageStorage(
    private val client: S3Client,
    private val presigner: S3Presigner,
    private val properties: StorageProperties,
) : ImageStorage {
    override fun put(
        key: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        client.putObject(
            PutObjectRequest
                .builder()
                .bucket(properties.bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes),
        )
    }

    override fun delete(key: String) {
        client.deleteObject(
            DeleteObjectRequest
                .builder()
                .bucket(properties.bucket)
                .key(key)
                .build(),
        )
    }

    override fun presignedGetUrl(key: String): String =
        presigner
            .presignGetObject(
                GetObjectPresignRequest
                    .builder()
                    .signatureDuration(properties.urlTtl)
                    .getObjectRequest(
                        GetObjectRequest
                            .builder()
                            .bucket(properties.bucket)
                            .key(key)
                            .build(),
                    ).build(),
            ).url()
            .toExternalForm()
}
