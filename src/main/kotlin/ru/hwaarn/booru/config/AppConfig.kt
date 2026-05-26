package ru.hwaarn.booru.config

import io.ktor.server.application.*

private fun ApplicationEnvironment.readRequired(path: String, env: String, fallback: String? = null): String {
    val fromConfig = config.propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }
    val fromEnv = System.getenv(env)?.takeIf { it.isNotBlank() }
    return fromEnv ?: fromConfig ?: fallback
    ?: error("Missing configuration for $path / env $env")
}

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maximumPoolSize: Int,
)

data class SecurityConfig(
    val issuer: String,
    val audience: String,
    val realm: String,
    val jwtSecret: String,
    val accessTokenTtlMinutes: Long,
    val refreshTokenTtlDays: Long,
)

data class StorageConfig(
    val uploadRoot: String,
    val publicBaseUrl: String,
)

data class ModerationConfig(
    val autoApproveMembers: Boolean,
)

data class AppConfig(
    val database: DatabaseConfig,
    val security: SecurityConfig,
    val storage: StorageConfig,
    val moderation: ModerationConfig,
) {
    companion object {
        fun from(environment: ApplicationEnvironment): AppConfig {
            val cfg = environment.config
            return AppConfig(
                database = DatabaseConfig(
                    url = environment.readRequired("app.database.url", "DATABASE_URL", "jdbc:postgresql://localhost:5432/booru"),
                    user = environment.readRequired("app.database.user", "DATABASE_USER", "booru"),
                    password = environment.readRequired("app.database.password", "DATABASE_PASSWORD", "booru"),
                    maximumPoolSize = cfg.propertyOrNull("app.database.maximumPoolSize")?.getString()?.toIntOrNull() ?: 10,
                ),
                security = SecurityConfig(
                    issuer = environment.readRequired("app.security.issuer", "JWT_ISSUER", "booru-backend"),
                    audience = environment.readRequired("app.security.audience", "JWT_AUDIENCE", "booru-mobile-client"),
                    realm = environment.readRequired("app.security.realm", "JWT_REALM", "booru-api"),
                    jwtSecret = environment.readRequired("app.security.jwtSecret", "JWT_SECRET", "change_me_now"),
                    accessTokenTtlMinutes = cfg.propertyOrNull("app.security.accessTokenTtlMinutes")?.getString()?.toLongOrNull() ?: 60,
                    refreshTokenTtlDays = cfg.propertyOrNull("app.security.refreshTokenTtlDays")?.getString()?.toLongOrNull() ?: 30,
                ),
                storage = StorageConfig(
                    uploadRoot = environment.readRequired("app.storage.uploadRoot", "UPLOAD_ROOT", "./data/uploads"),
                    publicBaseUrl = environment.readRequired("app.storage.publicBaseUrl", "PUBLIC_BASE_URL", "http://localhost:8080"),
                ),
                moderation = ModerationConfig(
                    autoApproveMembers = System.getenv("AUTO_APPROVE_MEMBERS")?.toBooleanStrictOrNull()
                        ?: cfg.propertyOrNull("app.moderation.autoApproveMembers")?.getString()?.toBooleanStrictOrNull()
                        ?: false,
                ),
            )
        }
    }
}
