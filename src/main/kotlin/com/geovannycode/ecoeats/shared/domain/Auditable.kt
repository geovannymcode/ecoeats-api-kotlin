package com.geovannycode.ecoeats.shared.domain

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@MappedSuperclass
internal abstract class Auditable {
    @field:CreationTimestamp
    @field:Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    @field:UpdateTimestamp
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
        protected set
}
