package ru.hwaarn.booru.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.server.auth.jwt.*
import ru.hwaarn.booru.model.UserRole
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class JwtProvider(private val config: SecurityConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)
    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withAudience(config.audience)
        .withIssuer(config.issuer)
        .build()

    fun issueAccessToken(userId: Long, username: String, role: UserRole): String {
        val expiresAt = Date.from(Instant.now().plus(config.accessTokenTtlMinutes, ChronoUnit.MINUTES))
        return JWT.create()
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withClaim("userId", userId)
            .withClaim("username", username)
            .withClaim("role", role.name)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }
}

data class UserSession(
    val userId: Long,
    val username: String,
    val role: UserRole,
)

fun JWTPrincipal.toUserSession(): UserSession = UserSession(
    userId = payload.getClaim("userId").asLong(),
    username = payload.getClaim("username").asString(),
    role = UserRole.valueOf(payload.getClaim("role").asString()),
)
