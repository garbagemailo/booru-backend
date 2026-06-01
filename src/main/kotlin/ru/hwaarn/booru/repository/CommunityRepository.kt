package ru.hwaarn.booru.repository

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

import ru.hwaarn.booru.db.Artists
import ru.hwaarn.booru.db.Comments
import ru.hwaarn.booru.db.DatabaseFactory
import ru.hwaarn.booru.db.Notes
import ru.hwaarn.booru.db.PoolPosts
import ru.hwaarn.booru.db.Pools
import ru.hwaarn.booru.db.PostAppeals
import ru.hwaarn.booru.db.PostFlags
import ru.hwaarn.booru.db.Posts
import ru.hwaarn.booru.db.TagSubscriptions
import ru.hwaarn.booru.db.Users
import ru.hwaarn.booru.db.WikiPageVersions
import ru.hwaarn.booru.db.WikiPages
import ru.hwaarn.booru.model.ArtistDto
import ru.hwaarn.booru.model.CommentDto
import ru.hwaarn.booru.model.CreateArtistRequest
import ru.hwaarn.booru.model.CreateCommentRequest
import ru.hwaarn.booru.model.CreateNoteRequest
import ru.hwaarn.booru.model.CreatePoolRequest
import ru.hwaarn.booru.model.CreateTagSubscriptionRequest
import ru.hwaarn.booru.model.CreateWikiPageRequest
import ru.hwaarn.booru.model.ModerationRecordDto
import ru.hwaarn.booru.model.ModerationRequest
import ru.hwaarn.booru.model.ModerationRecordStatus
import ru.hwaarn.booru.model.NoteDto
import ru.hwaarn.booru.model.PoolCategory
import ru.hwaarn.booru.model.PoolDto
import ru.hwaarn.booru.model.PostStatus
import ru.hwaarn.booru.model.TagSubscriptionDto
import ru.hwaarn.booru.model.UpdateArtistRequest
import ru.hwaarn.booru.model.UpdateCommentRequest
import ru.hwaarn.booru.model.UpdateNoteRequest
import ru.hwaarn.booru.model.UpdatePoolRequest
import ru.hwaarn.booru.model.UpdateWikiPageRequest
import ru.hwaarn.booru.model.UserDto
import ru.hwaarn.booru.model.UserRole
import ru.hwaarn.booru.model.WikiPageDto
import ru.hwaarn.booru.model.WikiVersionDto
import java.time.Instant

class CommunityRepository(
    private val postRepository: PostRepository,
    private val taxonomyRepository: TaxonomyRepository,
) {
    suspend fun listComments(postId: Long): List<CommentDto> = DatabaseFactory.dbQuery {
        val rows = Comments.selectAll().where { Comments.postId eq postId }
            .orderBy(Comments.createdAt, SortOrder.ASC)
            .toList()
        val userIds = rows.map { it[Comments.userId].value }.distinct()
        val users = loadUsers(userIds)
        rows.mapNotNull { row -> users[row[Comments.userId].value]?.let { row.toCommentDto(it) } }
    }

    suspend fun createComment(userId: Long, request: CreateCommentRequest): CommentDto? {
        val now = Instant.now()
        val postExists = DatabaseFactory.dbQuery {
            Posts.selectAll().where { Posts.id eq request.postId }.singleOrNull() != null
        }
        if (!postExists) return null

        val id = DatabaseFactory.dbQuery {
            Comments.insertAndGetId {
                it[Comments.postId] = request.postId
                it[Comments.userId] = userId
                it[Comments.body] = request.body
                it[Comments.isDeleted] = false
                it[Comments.createdAt] = now
                it[Comments.updatedAt] = now
            }.value
        }
        postRepository.incrementCommentCount(request.postId)
        return getComment(id)
    }

    suspend fun updateComment(commentId: Long, request: UpdateCommentRequest): CommentDto? {
        DatabaseFactory.dbQuery {
            Comments.update({ Comments.id eq commentId }) {
                it[Comments.body] = request.body
                it[Comments.updatedAt] = Instant.now()
            }
        }
        return getComment(commentId)
    }

    suspend fun deleteComment(commentId: Long): Boolean {
        val postId = DatabaseFactory.dbQuery {
            val row = Comments.selectAll().where { Comments.id eq commentId }.singleOrNull() ?: return@dbQuery null
            Comments.update({ Comments.id eq commentId }) {
                it[Comments.isDeleted] = true
                it[Comments.updatedAt] = Instant.now()
            }
            row[Comments.postId].value
        } ?: return false
        postRepository.incrementCommentCount(postId)
        return true
    }

    suspend fun getComment(commentId: Long): CommentDto? = DatabaseFactory.dbQuery {
        val row = Comments.selectAll().where { Comments.id eq commentId }.singleOrNull() ?: return@dbQuery null
        val user = loadUsers(listOf(row[Comments.userId].value))[row[Comments.userId].value] ?: return@dbQuery null
        row.toCommentDto(user)
    }

    suspend fun listNotes(postId: Long): List<NoteDto> = DatabaseFactory.dbQuery {
        Notes.selectAll().where { Notes.postId eq postId }
            .orderBy(Notes.createdAt, SortOrder.ASC)
            .map { it.toNoteDto() }
    }

    suspend fun createNote(userId: Long, request: CreateNoteRequest): NoteDto? {
        val postExists = DatabaseFactory.dbQuery {
            Posts.selectAll().where { Posts.id eq request.postId }.singleOrNull() != null
        }
        if (!postExists) return null
        val now = Instant.now()
        val id = DatabaseFactory.dbQuery {
            Notes.insertAndGetId {
                it[Notes.postId] = request.postId
                it[Notes.creatorId] = userId
                it[Notes.x] = request.x
                it[Notes.y] = request.y
                it[Notes.width] = request.width
                it[Notes.height] = request.height
                it[Notes.body] = request.body
                it[Notes.isActive] = true
                it[Notes.createdAt] = now
                it[Notes.updatedAt] = now
            }.value
        }
        return getNote(id)
    }

    suspend fun updateNote(noteId: Long, request: UpdateNoteRequest): NoteDto? {
        DatabaseFactory.dbQuery {
            Notes.update({ Notes.id eq noteId }) {
                request.x?.let { value -> it[Notes.x] = value }
                request.y?.let { value -> it[Notes.y] = value }
                request.width?.let { value -> it[Notes.width] = value }
                request.height?.let { value -> it[Notes.height] = value }
                request.body?.let { value -> it[Notes.body] = value }
                request.isActive?.let { value -> it[Notes.isActive] = value }
                it[Notes.updatedAt] = Instant.now()
            }
        }
        return getNote(noteId)
    }

    suspend fun deleteNote(noteId: Long): Boolean = DatabaseFactory.dbQuery {
        Notes.update({ Notes.id eq noteId }) {
            it[Notes.isActive] = false
            it[Notes.updatedAt] = Instant.now()
        } > 0
    }

    suspend fun getNote(noteId: Long): NoteDto? = DatabaseFactory.dbQuery {
        Notes.selectAll().where { Notes.id eq noteId }.singleOrNull()?.toNoteDto()
    }

    suspend fun getNoteCreatorId(noteId: Long): Long? = DatabaseFactory.dbQuery {
        Notes.selectAll().where { Notes.id eq noteId }.singleOrNull()?.get(Notes.creatorId)?.value
    }

    suspend fun createPool(userId: Long, request: CreatePoolRequest): PoolDto? {
        val now = Instant.now()
        val id = DatabaseFactory.dbQuery {
            Pools.insertAndGetId {
                it[Pools.name] = request.name
                it[Pools.description] = request.description
                it[Pools.category] = request.category.name
                it[Pools.isActive] = true
                it[Pools.isDeleted] = false
                it[Pools.creatorId] = userId
                it[Pools.createdAt] = now
                it[Pools.updatedAt] = now
            }.value
        }
        replacePoolPosts(id, request.postIds)
        return getPool(id)
    }

    suspend fun updatePool(poolId: Long, request: UpdatePoolRequest): PoolDto? {
        DatabaseFactory.dbQuery {
            Pools.update({ Pools.id eq poolId }) {
                request.name?.let { value -> it[Pools.name] = value }
                request.description?.let { value -> it[Pools.description] = value }
                request.category?.let { value -> it[Pools.category] = value.name }
                request.isActive?.let { value -> it[Pools.isActive] = value }
                it[Pools.updatedAt] = Instant.now()
            }
        }
        request.postIds?.let { replacePoolPosts(poolId, it) }
        return getPool(poolId)
    }

    suspend fun deletePool(poolId: Long): Boolean = DatabaseFactory.dbQuery {
        Pools.update({ Pools.id eq poolId }) {
            it[Pools.isDeleted] = true
            it[Pools.updatedAt] = Instant.now()
        } > 0
    }

    suspend fun listPools(): List<PoolDto> = DatabaseFactory.dbQuery {
        val rows = Pools.selectAll().orderBy(Pools.updatedAt, SortOrder.DESC).toList()
        val postsMap = loadPoolPostIds(rows.map { it[Pools.id].value })
        rows.map { row -> row.toPoolDto(postsMap) }
    }

    suspend fun getPool(poolId: Long): PoolDto? = DatabaseFactory.dbQuery {
        val row = Pools.selectAll().where { Pools.id eq poolId }.singleOrNull() ?: return@dbQuery null
        row.toPoolDto(loadPoolPostIds(listOf(poolId)))
    }

    suspend fun createWikiPage(userId: Long, request: CreateWikiPageRequest): WikiPageDto? {
        val now = Instant.now()
        val id = DatabaseFactory.dbQuery {
            WikiPages.insertAndGetId {
                it[WikiPages.title] = request.title
                it[WikiPages.body] = request.body
                it[WikiPages.otherNames] = request.otherNames.joinToString("\n")
                it[WikiPages.isLocked] = false
                it[WikiPages.creatorId] = userId
                it[WikiPages.updaterId] = userId
                it[WikiPages.createdAt] = now
                it[WikiPages.updatedAt] = now
            }.value
        }
        recordWikiVersion(id, userId)
        return getWikiPage(id)
    }

    suspend fun updateWikiPage(pageId: Long, userId: Long, request: UpdateWikiPageRequest): WikiPageDto? {
        DatabaseFactory.dbQuery {
            WikiPages.update({ WikiPages.id eq pageId }) {
                request.title?.let { value -> it[WikiPages.title] = value }
                request.body?.let { value -> it[WikiPages.body] = value }
                request.otherNames?.let { value -> it[WikiPages.otherNames] = value.joinToString("\n") }
                request.isLocked?.let { value -> it[WikiPages.isLocked] = value }
                it[WikiPages.updaterId] = userId
                it[WikiPages.updatedAt] = Instant.now()
            }
        }
        recordWikiVersion(pageId, userId)
        return getWikiPage(pageId)
    }

    suspend fun listWikiPages(query: String?): List<WikiPageDto> = DatabaseFactory.dbQuery {
        val op = if (query.isNullOrBlank()) Op.TRUE else (WikiPages.title like "%$query%")
        WikiPages.selectAll().where { op }.orderBy(WikiPages.updatedAt, SortOrder.DESC).map { it.toWikiDto() }
    }

    suspend fun getWikiPage(pageId: Long): WikiPageDto? = DatabaseFactory.dbQuery {
        WikiPages.selectAll().where { WikiPages.id eq pageId }.singleOrNull()?.toWikiDto()
    }

    suspend fun listWikiVersions(pageId: Long): List<WikiVersionDto> = DatabaseFactory.dbQuery {
        WikiPageVersions.selectAll().where { WikiPageVersions.pageId eq pageId }
            .orderBy(WikiPageVersions.createdAt, SortOrder.DESC)
            .map {
                WikiVersionDto(
                    id = it[WikiPageVersions.id].value,
                    pageId = it[WikiPageVersions.pageId].value,
                    updaterId = it[WikiPageVersions.updaterId].value,
                    title = it[WikiPageVersions.title],
                    body = it[WikiPageVersions.body],
                    otherNames = splitLines(it[WikiPageVersions.otherNames]),
                    createdAt = it[WikiPageVersions.createdAt].toString(),
                )
            }
    }

    suspend fun createArtist(userId: Long, request: CreateArtistRequest): ArtistDto? {
        val linkedTagId = request.linkedTagName
            ?.let { taxonomyRepository.ensureTags(listOf(it)).firstOrNull()?.id }
        val now = Instant.now()
        val id = DatabaseFactory.dbQuery {
            Artists.insertAndGetId {
                it[Artists.name] = request.name
                it[Artists.otherNames] = request.otherNames.joinToString("\n")
                it[Artists.groupName] = request.groupName
                it[Artists.urls] = request.urls.joinToString("\n")
                it[Artists.isBanned] = false
                it[Artists.isDeleted] = false
                it[Artists.linkedTagId] = linkedTagId
                it[Artists.creatorId] = userId
                it[Artists.createdAt] = now
                it[Artists.updatedAt] = now
            }.value
        }
        return getArtist(id)
    }

    suspend fun updateArtist(artistId: Long, request: UpdateArtistRequest): ArtistDto? {
        val linkedTagId = request.linkedTagName
            ?.let { taxonomyRepository.ensureTags(listOf(it)).firstOrNull()?.id }
        DatabaseFactory.dbQuery {
            Artists.update({ Artists.id eq artistId }) {
                request.name?.let { value -> it[Artists.name] = value }
                request.otherNames?.let { value -> it[Artists.otherNames] = value.joinToString("\n") }
                request.groupName?.let { value -> it[Artists.groupName] = value }
                request.urls?.let { value -> it[Artists.urls] = value.joinToString("\n") }
                request.isBanned?.let { value -> it[Artists.isBanned] = value }
                request.isDeleted?.let { value -> it[Artists.isDeleted] = value }
                if (request.linkedTagName != null) it[Artists.linkedTagId] = linkedTagId
                it[Artists.updatedAt] = Instant.now()
            }
        }
        return getArtist(artistId)
    }

    suspend fun deleteArtist(artistId: Long): Boolean = DatabaseFactory.dbQuery {
        Artists.update({ Artists.id eq artistId }) {
            it[Artists.isDeleted] = true
            it[Artists.updatedAt] = Instant.now()
        } > 0
    }

    suspend fun listArtists(query: String?): List<ArtistDto> = DatabaseFactory.dbQuery {
        val op = if (query.isNullOrBlank()) Op.TRUE else (Artists.name like "%$query%")
        Artists.selectAll().where { op }.orderBy(Artists.updatedAt, SortOrder.DESC).map { it.toArtistDto() }
    }

    suspend fun getArtist(artistId: Long): ArtistDto? = DatabaseFactory.dbQuery {
        Artists.selectAll().where { Artists.id eq artistId }.singleOrNull()?.toArtistDto()
    }

    suspend fun listSubscriptions(userId: Long): List<TagSubscriptionDto> = DatabaseFactory.dbQuery {
        TagSubscriptions.selectAll().where { TagSubscriptions.userId eq userId }
            .orderBy(TagSubscriptions.createdAt, SortOrder.DESC)
            .map { it.toSubscriptionDto() }
    }

    suspend fun createSubscription(userId: Long, request: CreateTagSubscriptionRequest): TagSubscriptionDto {
        val normalizedQuery = request.query.trim().replace(Regex("\\s+"), " ")
        require(normalizedQuery.isNotBlank()) { "Subscription query must not be blank" }
        val id = DatabaseFactory.dbQuery {
            TagSubscriptions.insertAndGetId {
                it[TagSubscriptions.userId] = userId
                it[TagSubscriptions.name] = request.name?.trim()?.ifBlank { null }
                it[TagSubscriptions.query] = normalizedQuery
                it[TagSubscriptions.createdAt] = Instant.now()
            }.value
        }
        return getSubscription(id) ?: error("Created subscription not found")
    }

    suspend fun getSubscription(subscriptionId: Long): TagSubscriptionDto? = DatabaseFactory.dbQuery {
        TagSubscriptions.selectAll().where { TagSubscriptions.id eq subscriptionId }.singleOrNull()?.toSubscriptionDto()
    }

    suspend fun getSubscriptionOwnerId(subscriptionId: Long): Long? = DatabaseFactory.dbQuery {
        TagSubscriptions.selectAll().where { TagSubscriptions.id eq subscriptionId }.singleOrNull()?.get(TagSubscriptions.userId)?.value
    }

    suspend fun deleteSubscription(subscriptionId: Long): Boolean = DatabaseFactory.dbQuery {
        TagSubscriptions.deleteWhere { TagSubscriptions.id eq subscriptionId } > 0
    }

    suspend fun createFlag(userId: Long, request: ModerationRequest): ModerationRecordDto? {
        val reason = request.reason.trim()
        require(reason.isNotBlank()) { "Report reason is required" }

        val canReport = DatabaseFactory.dbQuery {
            val post = Posts.selectAll().where { Posts.id eq request.postId }.singleOrNull() ?: return@dbQuery false
            val status = runCatching { PostStatus.valueOf(post[Posts.status]) }.getOrNull()
            post[Posts.uploaderId].value != userId && status == PostStatus.ACTIVE
        }
        if (!canReport) return null

        val id = DatabaseFactory.dbQuery {
            PostFlags.insertAndGetId {
                it[PostFlags.postId] = request.postId
                it[PostFlags.creatorId] = userId
                it[PostFlags.reason] = reason
                it[PostFlags.status] = ModerationRecordStatus.OPEN
                it[PostFlags.createdAt] = Instant.now()
                it[PostFlags.resolvedAt] = null
            }.value
        }
        return getFlag(id)
    }

    suspend fun createAppeal(userId: Long, request: ModerationRequest): ModerationRecordDto? {
        val reason = request.reason.trim()
        require(reason.isNotBlank()) { "Appeal reason is required" }

        val canAppeal = DatabaseFactory.dbQuery {
            val post = Posts.selectAll().where { Posts.id eq request.postId }.singleOrNull() ?: return@dbQuery false
            val status = runCatching { PostStatus.valueOf(post[Posts.status]) }.getOrNull()
            val hasOpenAppeal = PostAppeals.selectAll()
                .where { (PostAppeals.postId eq request.postId) and (PostAppeals.status eq ModerationRecordStatus.OPEN) }
                .count() > 0L
            post[Posts.uploaderId].value == userId && status in setOf(PostStatus.DELETED, PostStatus.REJECTED) && !hasOpenAppeal
        }
        if (!canAppeal) return null

        val id = DatabaseFactory.dbQuery {
            PostAppeals.insertAndGetId {
                it[PostAppeals.postId] = request.postId
                it[PostAppeals.creatorId] = userId
                it[PostAppeals.reason] = reason
                it[PostAppeals.status] = ModerationRecordStatus.OPEN
                it[PostAppeals.createdAt] = Instant.now()
                it[PostAppeals.resolvedAt] = null
            }.value
        }
        return getAppeal(id)
    }

    suspend fun resolveFlag(id: Long, resolverId: Long, status: String): ModerationRecordDto? = DatabaseFactory.dbQuery {
        val normalizedStatus = ModerationRecordStatus.normalize(status)
        val updated = PostFlags.update({ PostFlags.id eq id }) {
            it[PostFlags.status] = normalizedStatus
            it[PostFlags.resolverId] = resolverId
            it[PostFlags.resolvedAt] = Instant.now()
        }
        if (updated == 0) return@dbQuery null
        PostFlags.selectAll().where { PostFlags.id eq id }.singleOrNull()?.toFlagDto()
    }

    suspend fun resolveAppeal(id: Long, resolverId: Long, status: String): ModerationRecordDto? = DatabaseFactory.dbQuery {
        val normalizedStatus = ModerationRecordStatus.normalize(status)
        val updated = PostAppeals.update({ PostAppeals.id eq id }) {
            it[PostAppeals.status] = normalizedStatus
            it[PostAppeals.resolverId] = resolverId
            it[PostAppeals.resolvedAt] = Instant.now()
        }
        if (updated == 0) return@dbQuery null
        PostAppeals.selectAll().where { PostAppeals.id eq id }.singleOrNull()?.toAppealDto()
    }

    suspend fun getCommentAuthorId(commentId: Long): Long? = DatabaseFactory.dbQuery {
        Comments.selectAll().where { Comments.id eq commentId }.singleOrNull()?.get(Comments.userId)?.value
    }

    suspend fun getPoolCreatorId(poolId: Long): Long? = DatabaseFactory.dbQuery {
        Pools.selectAll().where { Pools.id eq poolId }.singleOrNull()?.get(Pools.creatorId)?.value
    }

    suspend fun getWikiCreatorId(pageId: Long): Long? = DatabaseFactory.dbQuery {
        WikiPages.selectAll().where { WikiPages.id eq pageId }.singleOrNull()?.get(WikiPages.creatorId)?.value
    }

    suspend fun getArtistCreatorId(artistId: Long): Long? = DatabaseFactory.dbQuery {
        Artists.selectAll().where { Artists.id eq artistId }.singleOrNull()?.get(Artists.creatorId)?.value
    }

    suspend fun listFlags(): List<ModerationRecordDto> = DatabaseFactory.dbQuery {
        PostFlags.selectAll()
            .where { PostFlags.status eq ModerationRecordStatus.OPEN }
            .orderBy(PostFlags.createdAt, SortOrder.DESC)
            .map { it.toFlagDto() }
    }

    suspend fun listAppeals(): List<ModerationRecordDto> = DatabaseFactory.dbQuery {
        PostAppeals.selectAll()
            .where { PostAppeals.status eq ModerationRecordStatus.OPEN }
            .orderBy(PostAppeals.createdAt, SortOrder.DESC)
            .map { it.toAppealDto() }
    }

    private suspend fun replacePoolPosts(poolId: Long, postIds: List<Long>) {
        DatabaseFactory.dbQuery {
            PoolPosts.deleteWhere { PoolPosts.poolId eq poolId }
            postIds.distinct().forEachIndexed { index, postId ->
                PoolPosts.insert {
                    it[PoolPosts.poolId] = poolId
                    it[PoolPosts.postId] = postId
                    it[PoolPosts.position] = index + 1
                }
            }
        }
    }

    private fun loadPoolPostIds(poolIds: List<Long>): Map<Long, List<Long>> {
        if (poolIds.isEmpty()) return emptyMap()
        return PoolPosts.selectAll().where { PoolPosts.poolId inList poolIds }
            .orderBy(PoolPosts.position, SortOrder.ASC)
            .groupBy { it[PoolPosts.poolId].value }
            .mapValues { (_, rows) -> rows.map { it[PoolPosts.postId].value } }
    }

    private suspend fun recordWikiVersion(pageId: Long, updaterId: Long) {
        DatabaseFactory.dbQuery {
            val page = WikiPages.selectAll().where { WikiPages.id eq pageId }.singleOrNull() ?: return@dbQuery
            WikiPageVersions.insert {
                it[WikiPageVersions.pageId] = pageId
                it[WikiPageVersions.updaterId] = updaterId
                it[WikiPageVersions.title] = page[WikiPages.title]
                it[WikiPageVersions.body] = page[WikiPages.body]
                it[WikiPageVersions.otherNames] = page[WikiPages.otherNames]
                it[WikiPageVersions.createdAt] = Instant.now()
            }
        }
    }

    private suspend fun getFlag(id: Long): ModerationRecordDto? = DatabaseFactory.dbQuery {
        PostFlags.selectAll().where { PostFlags.id eq id }.singleOrNull()?.toFlagDto()
    }

    private suspend fun getAppeal(id: Long): ModerationRecordDto? = DatabaseFactory.dbQuery {
        PostAppeals.selectAll().where { PostAppeals.id eq id }.singleOrNull()?.toAppealDto()
    }

    private fun loadUsers(userIds: List<Long>): Map<Long, UserDto> {
        if (userIds.isEmpty()) return emptyMap()
        return Users.selectAll().where { Users.id inList userIds }.associate { row ->
            row[Users.id].value to UserDto(
                id = row[Users.id].value,
                username = row[Users.username],
                email = row[Users.email],
                role = UserRole.valueOf(row[Users.role]),
                isActive = row[Users.isActive],
                createdAt = row[Users.createdAt].toString(),
            )
        }
    }

    private fun ResultRow.toCommentDto(author: UserDto): CommentDto = CommentDto(
        id = this[Comments.id].value,
        postId = this[Comments.postId].value,
        author = author,
        body = this[Comments.body],
        isDeleted = this[Comments.isDeleted],
        createdAt = this[Comments.createdAt].toString(),
        updatedAt = this[Comments.updatedAt].toString(),
    )

    private fun ResultRow.toNoteDto(): NoteDto = NoteDto(
        id = this[Notes.id].value,
        postId = this[Notes.postId].value,
        creatorId = this[Notes.creatorId].value,
        x = this[Notes.x],
        y = this[Notes.y],
        width = this[Notes.width],
        height = this[Notes.height],
        body = this[Notes.body],
        isActive = this[Notes.isActive],
        createdAt = this[Notes.createdAt].toString(),
        updatedAt = this[Notes.updatedAt].toString(),
    )

    private fun ResultRow.toPoolDto(postsMap: Map<Long, List<Long>>): PoolDto = PoolDto(
        id = this[Pools.id].value,
        name = this[Pools.name],
        description = this[Pools.description],
        category = PoolCategory.valueOf(this[Pools.category]),
        isActive = this[Pools.isActive],
        isDeleted = this[Pools.isDeleted],
        creatorId = this[Pools.creatorId].value,
        postIds = postsMap[this[Pools.id].value].orEmpty(),
        createdAt = this[Pools.createdAt].toString(),
        updatedAt = this[Pools.updatedAt].toString(),
    )

    private fun ResultRow.toWikiDto(): WikiPageDto = WikiPageDto(
        id = this[WikiPages.id].value,
        title = this[WikiPages.title],
        body = this[WikiPages.body],
        otherNames = splitLines(this[WikiPages.otherNames]),
        isLocked = this[WikiPages.isLocked],
        creatorId = this[WikiPages.creatorId].value,
        updaterId = this[WikiPages.updaterId].value,
        createdAt = this[WikiPages.createdAt].toString(),
        updatedAt = this[WikiPages.updatedAt].toString(),
    )

    private fun ResultRow.toArtistDto(): ArtistDto = ArtistDto(
        id = this[Artists.id].value,
        name = this[Artists.name],
        otherNames = splitLines(this[Artists.otherNames]),
        groupName = this[Artists.groupName],
        urls = splitLines(this[Artists.urls]),
        isBanned = this[Artists.isBanned],
        isDeleted = this[Artists.isDeleted],
        linkedTagId = this[Artists.linkedTagId]?.value,
        creatorId = this[Artists.creatorId].value,
        createdAt = this[Artists.createdAt].toString(),
        updatedAt = this[Artists.updatedAt].toString(),
    )

    private fun ResultRow.toSubscriptionDto(): TagSubscriptionDto = TagSubscriptionDto(
        id = this[TagSubscriptions.id].value,
        userId = this[TagSubscriptions.userId].value,
        name = this[TagSubscriptions.name],
        query = this[TagSubscriptions.query],
        createdAt = this[TagSubscriptions.createdAt].toString(),
    )

    private fun ResultRow.toFlagDto(): ModerationRecordDto = ModerationRecordDto(
        id = this[PostFlags.id].value,
        postId = this[PostFlags.postId].value,
        creatorId = this[PostFlags.creatorId].value,
        reason = this[PostFlags.reason],
        status = this[PostFlags.status],
        resolverId = this[PostFlags.resolverId]?.value,
        createdAt = this[PostFlags.createdAt].toString(),
        resolvedAt = this[PostFlags.resolvedAt]?.toString(),
    )

    private fun ResultRow.toAppealDto(): ModerationRecordDto = ModerationRecordDto(
        id = this[PostAppeals.id].value,
        postId = this[PostAppeals.postId].value,
        creatorId = this[PostAppeals.creatorId].value,
        reason = this[PostAppeals.reason],
        status = this[PostAppeals.status],
        resolverId = this[PostAppeals.resolverId]?.value,
        createdAt = this[PostAppeals.createdAt].toString(),
        resolvedAt = this[PostAppeals.resolvedAt]?.toString(),
    )

    private fun splitLines(value: String): List<String> = value
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
