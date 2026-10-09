package com.geovannycode.ecoeats.shared.security

import com.geovannycode.ecoeats.auth.infrastructure.UserRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Component
internal class UserPresenceCache(private val users: UserRepository, private val clock: Clock) {
    private val presentUntil = LinkedHashMap<UUID, Instant>()

    // Cache only existing accounts, cap memory, and recheck deletions after five seconds.
    @Synchronized
    fun exists(userId: UUID): Boolean {
        val now = clock.instant()
        if (presentUntil[userId]?.isAfter(now) == true) return true
        presentUntil.remove(userId)
        if (!users.existsById(userId)) return false
        if (presentUntil.size >= 10_000) presentUntil.remove(presentUntil.keys.first())
        presentUntil[userId] = now.plusSeconds(5)
        return true
    }
}
