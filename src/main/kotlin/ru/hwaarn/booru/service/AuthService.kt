package ru.hwaarn.booru.service

import ru.hwaarn.booru.config.JwtProvider
import ru.hwaarn.booru.config.SecurityConfig
import ru.hwaarn.booru.model.AuthRequest
import ru.hwaarn.booru.model.AuthResponse
import ru.hwaarn.booru.model.UserRole
import ru.hwaarn.booru.repository.UserRepository
import ru.hwaarn.booru.util.PasswordHasher
import java.util.UUID

class AuthService(
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
    private val securityConfig: SecurityConfig,
) {
    suspend fun register(username: String, email: String, password: String): AuthResponse {
        require(username.length in 3..64) { "Username must contain 3..64 characters" }
        require(email.contains("@")) { "Email is invalid" }
        require(password.length >= 8) { "Password must contain at least 8 characters" }
        val normalizedUsername = username.trim()
        val normalizedEmail = email.trim().lowercase()
        require(userRepository.getUserByUsername(normalizedUsername) == null) { "Username already exists" }
        require(userRepository.getUserByEmail(normalizedEmail) == null) { "Email already exists" }
        val role = if (userRepository.countUsers() == 0L) UserRole.ADMIN else UserRole.MEMBER
        val user = userRepository.createUser(normalizedUsername, normalizedEmail, PasswordHasher.hash(password), role)
        val refresh = UUID.randomUUID().toString()
        userRepository.storeRefreshToken(user.id, refresh, securityConfig.refreshTokenTtlDays)
        return AuthResponse(
            accessToken = jwtProvider.issueAccessToken(user.id, user.username, user.role),
            refreshToken = refresh,
            user = user,
        )
    }

    suspend fun login(request: AuthRequest): AuthResponse? {
        val normalizedLogin = request.login.trim().let { if (it.contains("@")) it.lowercase() else it }
        val credentials = userRepository.findCredentials(normalizedLogin) ?: return null
        val (id, hash, user) = credentials
        if (!user.isActive) return null
        if (userRepository.getActiveBan(user.id) != null) return null
        if (!PasswordHasher.verify(request.password, hash)) return null
        val refresh = UUID.randomUUID().toString()
        userRepository.storeRefreshToken(id, refresh, securityConfig.refreshTokenTtlDays)
        return AuthResponse(
            accessToken = jwtProvider.issueAccessToken(user.id, user.username, user.role),
            refreshToken = refresh,
            user = user,
        )
    }

    suspend fun refresh(rawRefreshToken: String): AuthResponse? {
        val userId = userRepository.consumeRefreshToken(rawRefreshToken) ?: return null
        val user = userRepository.getUserById(userId) ?: return null
        if (!user.isActive) return null
        if (userRepository.getActiveBan(user.id) != null) return null
        val nextRefresh = UUID.randomUUID().toString()
        userRepository.storeRefreshToken(userId, nextRefresh, securityConfig.refreshTokenTtlDays)
        return AuthResponse(
            accessToken = jwtProvider.issueAccessToken(user.id, user.username, user.role),
            refreshToken = nextRefresh,
            user = user,
        )
    }

    suspend fun logout(rawRefreshToken: String) {
        userRepository.revokeRefreshToken(rawRefreshToken)
    }
}
