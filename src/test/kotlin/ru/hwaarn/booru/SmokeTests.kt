package ru.hwaarn.booru

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.hwaarn.booru.model.PostRating
import ru.hwaarn.booru.model.PostStatus
import ru.hwaarn.booru.util.PasswordHasher
import ru.hwaarn.booru.util.PostSearchParser

class SmokeTests {
    @Test
    fun `password hasher verifies valid password`() {
        val password = "super_secret_123"
        val hash = PasswordHasher.hash(password)

        assertTrue(PasswordHasher.verify(password, hash))
        assertFalse(PasswordHasher.verify("wrong-password", hash))
    }

    @Test
    fun `post search parser extracts special tokens`() {
        val filters = PostSearchParser.parse("landscape -night rating:s status:pending user:test pool:42 order:score")

        assertEquals(listOf("landscape"), filters.positiveTags)
        assertEquals(listOf("night"), filters.negativeTags)
        assertEquals(PostRating.SAFE, filters.rating)
        assertEquals(PostStatus.PENDING, filters.status)
        assertEquals("test", filters.uploader)
        assertEquals(42L, filters.poolId)
        assertEquals("score", filters.order)
    }
}
