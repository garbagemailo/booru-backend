package ru.hwaarn.booru.model

object ModerationRecordStatus {
    const val OPEN = "OPEN"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"

    fun normalize(raw: String): String = raw.trim().uppercase()

    fun isApproved(raw: String): Boolean = raw.equals(APPROVED, ignoreCase = true)
}
