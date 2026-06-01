package ru.hwaarn.booru.service

private const val MIN_USERNAME_LENGTH = 3
private const val MAX_USERNAME_LENGTH = 64
private const val MIN_PASSWORD_LENGTH = 8

internal fun normalizeUsername(username: String): String = username.trim()

internal fun normalizeEmail(email: String): String = email.trim().lowercase()

internal fun normalizeLogin(login: String): String = login.trim().let { value ->
    if (value.contains("@")) value.lowercase() else value
}

internal fun validateRegistrationInput(username: String, email: String, password: String) {
    require(username.length in MIN_USERNAME_LENGTH..MAX_USERNAME_LENGTH) {
        "Username must contain $MIN_USERNAME_LENGTH..$MAX_USERNAME_LENGTH characters"
    }
    require(email.isValidRegistrationEmail()) { "Email is invalid" }
    require(password.length >= MIN_PASSWORD_LENGTH) { "Password must contain at least $MIN_PASSWORD_LENGTH characters" }
}

private fun String.isValidRegistrationEmail(): Boolean = contains("@")
