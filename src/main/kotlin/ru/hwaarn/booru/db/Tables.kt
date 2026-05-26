package ru.hwaarn.booru.db

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.timestamp


object Users : LongIdTable("users") {
    val username = varchar("username", 64).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = text("password_hash")
    val role = varchar("role", 32).default("MEMBER")
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object RefreshTokens : LongIdTable("refresh_tokens") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 128).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")
}

object Tags : LongIdTable("tags") {
    val name = varchar("name", 255).uniqueIndex()
    val category = varchar("category", 32).default("GENERAL")
    val postCount = integer("post_count").default(0)
    val isLocked = bool("is_locked").default(false)
    val isDeprecated = bool("is_deprecated").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object TagAliases : LongIdTable("tag_aliases") {
    val antecedentName = varchar("antecedent_name", 255).uniqueIndex()
    val consequentTagId = reference("consequent_tag_id", Tags, onDelete = ReferenceOption.CASCADE)
    val creatorId = reference("creator_id", Users, onDelete = ReferenceOption.RESTRICT)
    val createdAt = timestamp("created_at")
}

object TagImplications : LongIdTable("tag_implications") {
    val antecedentTagId = reference("antecedent_tag_id", Tags, onDelete = ReferenceOption.CASCADE)
    val consequentTagId = reference("consequent_tag_id", Tags, onDelete = ReferenceOption.CASCADE)
    val creatorId = reference("creator_id", Users, onDelete = ReferenceOption.RESTRICT)
    val createdAt = timestamp("created_at")

    init {
        uniqueIndex(antecedentTagId, consequentTagId)
    }
}

object Posts : LongIdTable("posts") {
    val uploaderId = reference("uploader_id", Users, onDelete = ReferenceOption.RESTRICT)
    val sourceUrl = text("source").nullable()
    val rating = varchar("rating", 16)
    val status = varchar("status", 32)
    val fileExt = varchar("file_ext", 16)
    val fileSize = long("file_size")
    val width = integer("width")
    val height = integer("height")
    val md5 = varchar("md5", 64)
    val sha256 = varchar("sha256", 128).uniqueIndex()
    val tagString = text("tag_string")
    val parentPostId = reference("parent_post_id", Posts, onDelete = ReferenceOption.SET_NULL).nullable()
    val score = integer("score").default(0)
    val favoritesCount = integer("favorites_count").default(0)
    val commentCount = integer("comment_count").default(0)
    val originalPath = text("original_path")
    val previewPath = text("preview_path")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object PostTags : Table("post_tags") {
    val postId = reference("post_id", Posts, onDelete = ReferenceOption.CASCADE)
    val tagId = reference("tag_id", Tags, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(postId, tagId)
}

object PostVersions : LongIdTable("post_versions") {
    val postId = reference("post_id", Posts, onDelete = ReferenceOption.CASCADE)
    val updaterId = reference("updater_id", Users, onDelete = ReferenceOption.RESTRICT)
    val snapshotJson = text("snapshot_json")
    val createdAt = timestamp("created_at")
}

object Uploads : LongIdTable("uploads") {
    val postId = reference("post_id", Posts, onDelete = ReferenceOption.CASCADE)
    val uploaderId = reference("uploader_id", Users, onDelete = ReferenceOption.RESTRICT)
    val fileName = varchar("file_name", 255)
    val sourceUrl = text("source").nullable()
    val createdAt = timestamp("created_at")
}

object Favorites : Table("favorites") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val postId = reference("post_id", Posts, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(userId, postId)
}

object PostVotes : Table("post_votes") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val postId = reference("post_id", Posts, onDelete = ReferenceOption.CASCADE)
    val score = integer("score")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(userId, postId)
}

object Comments : LongIdTable("comments") {
    val postId = reference("post_id", Posts, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val body = text("body")
    val isDeleted = bool("is_deleted").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object Notes : LongIdTable("notes") {
    val postId = reference("post_id", Posts, onDelete = ReferenceOption.CASCADE)
    val creatorId = reference("creator_id", Users, onDelete = ReferenceOption.CASCADE)
    val x = integer("x")
    val y = integer("y")
    val width = integer("width")
    val height = integer("height")
    val body = text("body")
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object Pools : LongIdTable("pools") {
    val name = varchar("name", 255).uniqueIndex()
    val description = text("description")
    val category = varchar("category", 32)
    val isActive = bool("is_active").default(true)
    val isDeleted = bool("is_deleted").default(false)
    val creatorId = reference("creator_id", Users, onDelete = ReferenceOption.RESTRICT)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object PoolPosts : Table("pool_posts") {
    val poolId = reference("pool_id", Pools, onDelete = ReferenceOption.CASCADE)
    val postId = reference("post_id", Posts, onDelete = ReferenceOption.CASCADE)
    val position = integer("position")

    override val primaryKey = PrimaryKey(poolId, postId)
}

object WikiPages : LongIdTable("wiki_pages") {
    val title = varchar("title", 255).uniqueIndex()
    val body = text("body")
    val otherNames = text("other_names").default("")
    val isLocked = bool("is_locked").default(false)
    val creatorId = reference("creator_id", Users, onDelete = ReferenceOption.RESTRICT)
    val updaterId = reference("updater_id", Users, onDelete = ReferenceOption.RESTRICT)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object WikiPageVersions : LongIdTable("wiki_page_versions") {
    val pageId = reference("page_id", WikiPages, onDelete = ReferenceOption.CASCADE)
    val updaterId = reference("updater_id", Users, onDelete = ReferenceOption.RESTRICT)
    val title = varchar("title", 255)
    val body = text("body")
    val otherNames = text("other_names").default("")
    val createdAt = timestamp("created_at")
}

object Artists : LongIdTable("artists") {
    val name = varchar("name", 255).uniqueIndex()
    val otherNames = text("other_names").default("")
    val groupName = varchar("group_name", 255).nullable()
    val urls = text("urls").default("")
    val isBanned = bool("is_banned").default(false)
    val isDeleted = bool("is_deleted").default(false)
    val linkedTagId = reference("linked_tag_id", Tags, onDelete = ReferenceOption.SET_NULL).nullable()
    val creatorId = reference("creator_id", Users, onDelete = ReferenceOption.RESTRICT)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object TagSubscriptions : LongIdTable("tag_subscriptions") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255).nullable()
    val query = text("query")
    val createdAt = timestamp("created_at")

    init {
        uniqueIndex(userId, query)
    }
}

object PostFlags : LongIdTable("post_flags") {
    val postId = reference("post_id", Posts, onDelete = ReferenceOption.CASCADE)
    val creatorId = reference("creator_id", Users, onDelete = ReferenceOption.RESTRICT)
    val reason = text("reason")
    val status = varchar("status", 32).default("OPEN")
    val resolverId = reference("resolver_id", Users, onDelete = ReferenceOption.SET_NULL).nullable()
    val createdAt = timestamp("created_at")
    val resolvedAt = timestamp("resolved_at").nullable()
}

object PostAppeals : LongIdTable("post_appeals") {
    val postId = reference("post_id", Posts, onDelete = ReferenceOption.CASCADE)
    val creatorId = reference("creator_id", Users, onDelete = ReferenceOption.RESTRICT)
    val reason = text("reason")
    val status = varchar("status", 32).default("OPEN")
    val resolverId = reference("resolver_id", Users, onDelete = ReferenceOption.SET_NULL).nullable()
    val createdAt = timestamp("created_at")
    val resolvedAt = timestamp("resolved_at").nullable()
}

object UserBans : LongIdTable("user_bans") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val bannerId = reference("banner_id", Users, onDelete = ReferenceOption.RESTRICT)
    val reason = text("reason")
    val expiresAt = timestamp("expires_at").nullable()
    val isActive = bool("is_active").default(true)
    val revokedAt = timestamp("revoked_at").nullable()
    val revokedBy = reference("revoked_by", Users, onDelete = ReferenceOption.SET_NULL).nullable()
    val createdAt = timestamp("created_at")
}

object AuditLogs : LongIdTable("audit_logs") {
    val actorId = reference("actor_id", Users, onDelete = ReferenceOption.SET_NULL).nullable()
    val action = varchar("action", 128)
    val entityType = varchar("entity_type", 64)
    val entityId = long("entity_id").nullable()
    val details = text("details").nullable()
    val createdAt = timestamp("created_at")
}
