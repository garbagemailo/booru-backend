package ru.hwaarn.booru.repository

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

import ru.hwaarn.booru.db.DatabaseFactory.dbQuery
import ru.hwaarn.booru.db.RefreshTokens
import ru.hwaarn.booru.db.UserBans
import ru.hwaarn.booru.db.Users
import ru.hwaarn.booru.model.UserBanDto
import ru.hwaarn.booru.model.UserDto
import ru.hwaarn.booru.model.UserRole
import ru.hwaarn.booru.util.Digests
import java.time.Instant
import java.time.temporal.ChronoUnit

class UserRepository {
    suspend fun createUser(username: String, email: String, passwordHash: String, role: UserRole = UserRole.MEMBER): UserDto = dbQuery {
        val now = Instant.now()
        val id = Users.insertAndGetId {
            it[Users.username] = username
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[Users.role] = role.name
            it[Users.isActive] = true
            it[Users.createdAt] = now
            it[Users.updatedAt] = now
        }.value
        Users.selectAll().where { Users.id eq id }.single().toUserDto()
    }

    suspend fun getUserById(id: Long): UserDto? = dbQuery {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUserDto()
    }

    suspend fun getUserByUsername(username: String): UserDto? = dbQuery {
        Users.selectAll().where { Users.username eq username }.singleOrNull()?.toUserDto()
    }

    suspend fun getUserByEmail(email: String): UserDto? = dbQuery {
        Users.selectAll().where { Users.email eq email }.singleOrNull()?.toUserDto()
    }

    suspend fun findCredentials(login: String): Triple<Long, String, UserDto>? = dbQuery {
        Users.selectAll().where {
            (Users.username eq login) or (Users.email eq login)
        }.singleOrNull()?.let { Triple(it[Users.id].value, it[Users.passwordHash], it.toUserDto()) }
    }

    suspend fun countUsers(): Long = dbQuery {
        Users.selectAll().count()
    }

    suspend fun listUsers(): List<UserDto> = dbQuery {
        Users.selectAll().orderBy(Users.createdAt, SortOrder.DESC).map { it.toUserDto() }
    }

    suspend fun updateRole(id: Long, role: UserRole): UserDto? = dbQuery {
        Users.update({ Users.id eq id }) {
            it[Users.role] = role.name
            it[Users.updatedAt] = Instant.now()
        }
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUserDto()
    }

    suspend fun storeRefreshToken(userId: Long, rawToken: String, ttlDays: Long) = dbQuery {
        RefreshTokens.insert {
            it[RefreshTokens.userId] = userId
            it[RefreshTokens.tokenHash] = Digests.sha256(rawToken)
            it[RefreshTokens.expiresAt] = Instant.now().plus(ttlDays, ChronoUnit.DAYS)
            it[RefreshTokens.createdAt] = Instant.now()
        }
    }

    suspend fun consumeRefreshToken(rawToken: String): Long? = dbQuery {
        val hashed = Digests.sha256(rawToken)
        val token = RefreshTokens.selectAll().where {
            (RefreshTokens.tokenHash eq hashed) and (RefreshTokens.expiresAt greaterEq Instant.now())
        }.singleOrNull()
        if (token != null) {
            RefreshTokens.deleteWhere { RefreshTokens.tokenHash eq hashed }
            token[RefreshTokens.userId].value
        } else null
    }

    suspend fun revokeRefreshToken(rawToken: String) = dbQuery {
        RefreshTokens.deleteWhere { RefreshTokens.tokenHash eq Digests.sha256(rawToken) }
    }

    suspend fun createBan(userId: Long, bannerId: Long, reason: String, expiresAt: Instant?): UserBanDto = dbQuery {
        val now = Instant.now()
        UserBans.update({ (UserBans.userId eq userId) and (UserBans.isActive eq true) }) {
            it[UserBans.isActive] = false
            it[UserBans.revokedAt] = now
            it[UserBans.revokedBy] = bannerId
        }
        val id = UserBans.insertAndGetId {
            it[UserBans.userId] = userId
            it[UserBans.bannerId] = bannerId
            it[UserBans.reason] = reason
            it[UserBans.expiresAt] = expiresAt
            it[UserBans.isActive] = true
            it[UserBans.revokedAt] = null
            it[UserBans.revokedBy] = null
            it[UserBans.createdAt] = now
        }.value
        Users.update({ Users.id eq userId }) {
            it[Users.isActive] = false
            it[Users.updatedAt] = now
        }
        UserBans.selectAll().where { UserBans.id eq id }.single().toUserBanDto()
    }

    suspend fun revokeBan(banId: Long, revokedBy: Long): UserBanDto? = dbQuery {
        val ban = UserBans.selectAll().where { UserBans.id eq banId }.singleOrNull() ?: return@dbQuery null
        val now = Instant.now()
        UserBans.update({ UserBans.id eq banId }) {
            it[UserBans.isActive] = false
            it[UserBans.revokedAt] = now
            it[UserBans.revokedBy] = revokedBy
        }
        val userId = ban[UserBans.userId].value
        val hasOtherActiveBan = UserBans.selectAll().where {
            (UserBans.userId eq userId) and (UserBans.isActive eq true) and (UserBans.id neq banId)
        }.any()
        if (!hasOtherActiveBan) {
            Users.update({ Users.id eq userId }) {
                it[Users.isActive] = true
                it[Users.updatedAt] = now
            }
        }
        UserBans.selectAll().where { UserBans.id eq banId }.singleOrNull()?.toUserBanDto()
    }

    suspend fun listBans(activeOnly: Boolean): List<UserBanDto> = dbQuery {
        val rows = if (activeOnly) {
            UserBans.selectAll().where { UserBans.isActive eq true }
        } else {
            UserBans.selectAll()
        }
        rows.orderBy(UserBans.createdAt, SortOrder.DESC).map { it.toUserBanDto() }
    }

    suspend fun getActiveBan(userId: Long): UserBanDto? = dbQuery {
        val now = Instant.now()
        val row = UserBans.selectAll().where {
            (UserBans.userId eq userId) and (UserBans.isActive eq true)
        }.orderBy(UserBans.createdAt, SortOrder.DESC).limit(1).singleOrNull() ?: return@dbQuery null

        val expiresAt = row[UserBans.expiresAt]
        if (expiresAt != null && expiresAt.isBefore(now)) {
            UserBans.update({ UserBans.id eq row[UserBans.id].value }) {
                it[UserBans.isActive] = false
                it[UserBans.revokedAt] = now
                it[UserBans.revokedBy] = null
            }
            Users.update({ Users.id eq userId }) {
                it[Users.isActive] = true
                it[Users.updatedAt] = now
            }
            return@dbQuery null
        }
        row.toUserBanDto()
    }

    private fun ResultRow.toUserDto(): UserDto = UserDto(
        id = this[Users.id].value,
        username = this[Users.username],
        email = this[Users.email],
        role = UserRole.valueOf(this[Users.role]),
        isActive = this[Users.isActive],
        createdAt = this[Users.createdAt].toString(),
    )

    private fun ResultRow.toUserBanDto(): UserBanDto = UserBanDto(
        id = this[UserBans.id].value,
        userId = this[UserBans.userId].value,
        bannerId = this[UserBans.bannerId].value,
        reason = this[UserBans.reason],
        expiresAt = this[UserBans.expiresAt]?.toString(),
        isActive = this[UserBans.isActive],
        revokedAt = this[UserBans.revokedAt]?.toString(),
        revokedBy = this[UserBans.revokedBy]?.value,
        createdAt = this[UserBans.createdAt].toString(),
    )
}
