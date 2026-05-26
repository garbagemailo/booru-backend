package ru.hwaarn.booru.util

import ru.hwaarn.booru.model.PostRating
import ru.hwaarn.booru.model.PostStatus


data class PostSearchFilters(
    val positiveTags: List<String>,
    val negativeTags: List<String>,
    val rating: PostRating? = null,
    val status: PostStatus? = null,
    val uploader: String? = null,
    val poolId: Long? = null,
    val order: String = "newest",
)

object PostSearchParser {
    fun parse(raw: String?): PostSearchFilters {
        if (raw.isNullOrBlank()) return PostSearchFilters(emptyList(), emptyList())
        var rating: PostRating? = null
        var status: PostStatus? = null
        var uploader: String? = null
        var poolId: Long? = null
        var order = "newest"
        val positive = mutableListOf<String>()
        val negative = mutableListOf<String>()

        raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { token ->
            val normalized = token.lowercase()
            when {
                normalized.startsWith("rating:") -> {
                    rating = when (normalized.substringAfter(":")) {
                        "s", "safe" -> PostRating.SAFE
                        "q", "questionable" -> PostRating.QUESTIONABLE
                        "e", "explicit" -> PostRating.EXPLICIT
                        else -> null
                    }
                }
                normalized.startsWith("status:") -> {
                    status = runCatching { PostStatus.valueOf(normalized.substringAfter(":").uppercase()) }.getOrNull()
                }
                normalized.startsWith("user:") || normalized.startsWith("uploader:") -> {
                    uploader = token.substringAfter(":")
                }
                normalized.startsWith("pool:") -> {
                    poolId = token.substringAfter(":").toLongOrNull()
                }
                normalized.startsWith("order:") -> {
                    order = token.substringAfter(":")
                }
                normalized.startsWith("-") -> negative += normalized.removePrefix("-")
                else -> positive += normalized
            }
        }
        return PostSearchFilters(positive, negative, rating, status, uploader, poolId, order)
    }
}
