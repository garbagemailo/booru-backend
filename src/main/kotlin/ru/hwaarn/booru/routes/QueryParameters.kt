package ru.hwaarn.booru.routes

import io.ktor.http.Parameters

private const val DEFAULT_PAGE = 1
private const val DEFAULT_LIMIT = 20
private const val DEFAULT_UPLOAD_LIMIT = 50
private const val DEFAULT_AUDIT_LIMIT = 100

internal fun Parameters.page(default: Int = DEFAULT_PAGE): Int = intOrDefault("page", default).coerceAtLeast(1)

internal fun Parameters.limit(default: Int = DEFAULT_LIMIT, range: IntRange = 1..100): Int =
    intOrDefault("limit", default).coerceIn(range)

internal fun Parameters.uploadLimit(): Int = limit(default = DEFAULT_UPLOAD_LIMIT, range = 1..100)

internal fun Parameters.auditLimit(): Int = limit(default = DEFAULT_AUDIT_LIMIT, range = 1..500)

private fun Parameters.intOrDefault(name: String, default: Int): Int = this[name]?.toIntOrNull() ?: default
