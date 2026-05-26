package ru.hwaarn.booru.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole { MEMBER, JANITOR, MODERATOR, ADMIN }

@Serializable
enum class TagCategory { GENERAL, ARTIST, CHARACTER, COPYRIGHT, META }

@Serializable
enum class PostRating { SAFE, QUESTIONABLE, EXPLICIT }

@Serializable
enum class PostStatus { PENDING, ACTIVE, DELETED, REJECTED }

@Serializable
enum class PoolCategory { SERIES, COLLECTION }

@Serializable
data class ApiError(val message: String)

@Serializable
data class RegisterRequest(val username: String, val email: String, val password: String)

@Serializable
data class AuthRequest(val login: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    val email: String,
    val role: UserRole,
    val isActive: Boolean,
    val createdAt: String,
)

@Serializable
data class UpdateUserRoleRequest(val role: UserRole)

@Serializable
data class TagDto(
    val id: Long,
    val name: String,
    val category: TagCategory,
    val postCount: Int,
    val isLocked: Boolean,
    val isDeprecated: Boolean,
)

@Serializable
data class CreateAliasRequest(val antecedentName: String, val consequentName: String)

@Serializable
data class CreateImplicationRequest(val antecedentName: String, val consequentName: String)

@Serializable
data class UploadMetadata(
    val tags: String,
    val rating: PostRating,
    val source: String? = null,
    val parentId: Long? = null,
)

@Serializable
data class UpdatePostRequest(
    val tags: String? = null,
    val rating: PostRating? = null,
    val source: String? = null,
    val parentId: Long? = null,
    val status: PostStatus? = null,
)

@Serializable
data class VoteRequest(val score: Int)

@Serializable
data class PostDto(
    val id: Long,
    val uploader: UserDto,
    val source: String?,
    val rating: PostRating,
    val status: PostStatus,
    val fileExt: String,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val tagString: String,
    val tags: List<String>,
    val parentId: Long?,
    val score: Int,
    val favoritesCount: Int,
    val commentCount: Int,
    val originalUrl: String,
    val previewUrl: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class UploadDto(
    val id: Long,
    val postId: Long,
    val uploaderId: Long,
    val fileName: String,
    val source: String?,
    val createdAt: String,
)

@Serializable
data class CommentDto(
    val id: Long,
    val postId: Long,
    val author: UserDto,
    val body: String,
    val isDeleted: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class CreateCommentRequest(val postId: Long, val body: String)

@Serializable
data class UpdateCommentRequest(val body: String)

@Serializable
data class NoteDto(
    val id: Long,
    val postId: Long,
    val creatorId: Long,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val body: String,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class CreateNoteRequest(
    val postId: Long,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val body: String,
)

@Serializable
data class UpdateNoteRequest(
    val x: Int? = null,
    val y: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val body: String? = null,
    val isActive: Boolean? = null,
)

@Serializable
data class PoolDto(
    val id: Long,
    val name: String,
    val description: String,
    val category: PoolCategory,
    val isActive: Boolean,
    val isDeleted: Boolean,
    val creatorId: Long,
    val postIds: List<Long>,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class CreatePoolRequest(
    val name: String,
    val description: String,
    val category: PoolCategory,
    val postIds: List<Long> = emptyList(),
)

@Serializable
data class UpdatePoolRequest(
    val name: String? = null,
    val description: String? = null,
    val category: PoolCategory? = null,
    val isActive: Boolean? = null,
    val postIds: List<Long>? = null,
)

@Serializable
data class WikiPageDto(
    val id: Long,
    val title: String,
    val body: String,
    val otherNames: List<String>,
    val isLocked: Boolean,
    val creatorId: Long,
    val updaterId: Long,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class CreateWikiPageRequest(val title: String, val body: String, val otherNames: List<String> = emptyList())

@Serializable
data class UpdateWikiPageRequest(
    val title: String? = null,
    val body: String? = null,
    val otherNames: List<String>? = null,
    val isLocked: Boolean? = null,
)

@Serializable
data class WikiVersionDto(
    val id: Long,
    val pageId: Long,
    val updaterId: Long,
    val title: String,
    val body: String,
    val otherNames: List<String>,
    val createdAt: String,
)

@Serializable
data class ArtistDto(
    val id: Long,
    val name: String,
    val otherNames: List<String>,
    val groupName: String?,
    val urls: List<String>,
    val isBanned: Boolean,
    val isDeleted: Boolean,
    val linkedTagId: Long?,
    val creatorId: Long,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class CreateArtistRequest(
    val name: String,
    val otherNames: List<String> = emptyList(),
    val groupName: String? = null,
    val urls: List<String> = emptyList(),
    val linkedTagName: String? = null,
)

@Serializable
data class UpdateArtistRequest(
    val name: String? = null,
    val otherNames: List<String>? = null,
    val groupName: String? = null,
    val urls: List<String>? = null,
    val isBanned: Boolean? = null,
    val isDeleted: Boolean? = null,
    val linkedTagName: String? = null,
)

@Serializable
data class TagSubscriptionDto(
    val id: Long,
    val userId: Long,
    val name: String?,
    val query: String,
    val createdAt: String,
)

@Serializable
data class CreateTagSubscriptionRequest(
    val query: String,
    val name: String? = null,
)

@Serializable
data class ModerationRequest(val postId: Long, val reason: String)

@Serializable
data class ModerationResolutionRequest(val status: String)

@Serializable
data class ModerationRecordDto(
    val id: Long,
    val postId: Long,
    val creatorId: Long,
    val reason: String,
    val status: String,
    val resolverId: Long?,
    val createdAt: String,
    val resolvedAt: String?,
)

@Serializable
data class CreateUserBanRequest(
    val userId: Long,
    val reason: String,
    val expiresAt: String? = null,
)

@Serializable
data class UserBanDto(
    val id: Long,
    val userId: Long,
    val bannerId: Long,
    val reason: String,
    val expiresAt: String?,
    val isActive: Boolean,
    val revokedAt: String?,
    val revokedBy: Long?,
    val createdAt: String,
)

@Serializable
data class AuditLogDto(
    val id: Long,
    val actorId: Long?,
    val action: String,
    val entityType: String,
    val entityId: Long?,
    val details: String?,
    val createdAt: String,
)

@Serializable
data class PostVersionDto(
    val id: Long,
    val postId: Long,
    val updaterId: Long,
    val snapshotJson: String,
    val createdAt: String,
)

@Serializable
data class PagedResponse<T>(
    val page: Int,
    val limit: Int,
    val total: Long,
    val items: List<T>,
)
