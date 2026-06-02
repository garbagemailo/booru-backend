package ru.hwaarn.booru.repository

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

import ru.hwaarn.booru.db.*
import ru.hwaarn.booru.model.*
import ru.hwaarn.booru.util.PostSearchParser
import ru.hwaarn.booru.util.StorageService
import java.time.Instant
import java.time.temporal.ChronoUnit

class PostRepository(
    private val taxonomyRepository: TaxonomyRepository,
    private val storageService: StorageService,
) {
    private data class DeletedFileSet(
        val id: Long,
        val originalPath: String,
        val previewPath: String,
    )

    suspend fun createPost(
        uploaderId: Long,
        metadata: UploadMetadata,
        storedFile: StorageService.StoredFile,
        initialStatus: PostStatus,
    ): PostDto {
        val now = Instant.now()
        val postId = DatabaseFactory.dbQuery {
            Posts.insertAndGetId {
                it[Posts.uploaderId] = uploaderId
                it[Posts.sourceUrl] = metadata.source
                it[Posts.rating] = metadata.rating.name
                it[Posts.status] = initialStatus.name
                it[Posts.fileExt] = storedFile.ext
                it[Posts.fileSize] = storedFile.fileSize
                it[Posts.width] = storedFile.width
                it[Posts.height] = storedFile.height
                it[Posts.md5] = storedFile.md5
                it[Posts.sha256] = storedFile.sha256
                it[Posts.tagString] = ""
                it[Posts.parentPostId] = metadata.parentId
                it[Posts.score] = 0
                it[Posts.favoritesCount] = 0
                it[Posts.commentCount] = 0
                it[Posts.originalPath] = storedFile.originalPath
                it[Posts.previewPath] = storedFile.previewPath
                it[Posts.createdAt] = now
                it[Posts.updatedAt] = now
            }.value
        }

        DatabaseFactory.dbQuery {
            Uploads.insert {
                it[Uploads.postId] = postId
                it[Uploads.uploaderId] = uploaderId
                it[Uploads.fileName] = storedFile.originalFileName
                it[Uploads.sourceUrl] = metadata.source
                it[Uploads.createdAt] = now
            }
        }

        attachTags(postId, metadata.tags)
        snapshot(postId, uploaderId)
        return getPost(postId) ?: error("Created post not found")
    }

    suspend fun updatePost(postId: Long, updaterId: Long, request: UpdatePostRequest): PostDto? {
        DatabaseFactory.dbQuery {
            Posts.update({ Posts.id eq postId }) {
                request.source?.let { source -> it[Posts.sourceUrl] = source }
                request.rating?.let { rating -> it[Posts.rating] = rating.name }
                request.parentId?.let { parentId -> it[Posts.parentPostId] = parentId }
                request.status?.let { status -> it[Posts.status] = status.name }
                it[Posts.updatedAt] = Instant.now()
            }
        }
        if (request.tags != null) attachTags(postId, request.tags)
        snapshot(postId, updaterId)
        return getPost(postId)
    }

    suspend fun getPost(postId: Long): PostDto? = DatabaseFactory.dbQuery {
        val row = Posts.selectAll().where { Posts.id eq postId }.singleOrNull() ?: return@dbQuery null
        val user = loadUsers(listOf(row[Posts.uploaderId].value))[row[Posts.uploaderId].value] ?: return@dbQuery null
        val tags = loadPostTags(listOf(postId))[postId].orEmpty()
        row.toPostDto(user, tags)
    }

    suspend fun getPostBySha256(sha256: String): PostDto? = DatabaseFactory.dbQuery {
        val row = Posts.selectAll().where { Posts.sha256 eq sha256 }.singleOrNull() ?: return@dbQuery null
        val postId = row[Posts.id].value
        val user = loadUsers(listOf(row[Posts.uploaderId].value))[row[Posts.uploaderId].value] ?: return@dbQuery null
        val tags = loadPostTags(listOf(postId))[postId].orEmpty()
        row.toPostDto(user, tags)
    }

    suspend fun searchPosts(rawTags: String?, page: Int, limit: Int): PagedResponse<PostDto> {
        val safePage = page.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(1, 100)
        val filters = PostSearchParser.parse(rawTags)
        val positive = taxonomyRepository.normalizeForSearch(filters.positiveTags)
        val negative = taxonomyRepository.normalizeForSearch(filters.negativeTags)

        return DatabaseFactory.dbQuery {
            var op: Op<Boolean> = Op.TRUE
            filters.rating?.let { op = op and (Posts.rating eq it.name) }
            if (filters.status != null) {
                op = op and (Posts.status eq filters.status.name)
            } else {
                op = op and (Posts.status eq PostStatus.ACTIVE.name)
            }
            if (filters.uploader != null) {
                val uploaderId = Users.selectAll().where { Users.username eq filters.uploader }
                    .singleOrNull()?.get(Users.id)?.value
                if (uploaderId == null) return@dbQuery PagedResponse(safePage, safeLimit, 0, emptyList())
                op = op and (Posts.uploaderId eq uploaderId)
            }

            val baseIds = Posts.selectAll().where { op }.map { it[Posts.id].value }.toMutableSet()
            if (filters.poolId != null) {
                val poolIds = PoolPosts.selectAll().where { PoolPosts.poolId eq filters.poolId }.map { it[PoolPosts.postId].value }.toSet()
                baseIds.retainAll(poolIds)
            }

            if (positive.isNotEmpty()) {
                val positiveTagIds = Tags.selectAll().where { Tags.name inList positive }.map { it[Tags.id].value }
                if (positiveTagIds.size != positive.size) {
                    return@dbQuery PagedResponse(safePage, safeLimit, 0, emptyList())
                }
                val intersections = positiveTagIds.map { tagId ->
                    PostTags.selectAll().where { PostTags.tagId eq tagId }.map { it[PostTags.postId].value }.toSet()
                }
                val allowed = intersections.reduce { acc, set -> acc.intersect(set) }
                baseIds.retainAll(allowed)
            }

            if (negative.isNotEmpty()) {
                val negativeTagIds = Tags.selectAll().where { Tags.name inList negative }.map { it[Tags.id].value }
                val blocked = negativeTagIds.flatMap { tagId ->
                    PostTags.selectAll().where { PostTags.tagId eq tagId }.map { it[PostTags.postId].value }
                }.toSet()
                baseIds.removeAll(blocked)
            }

            val total = baseIds.size.toLong()
            if (baseIds.isEmpty()) {
                return@dbQuery PagedResponse(safePage, safeLimit, total, emptyList())
            }

            val orderBy = when (filters.order.lowercase()) {
                "score", "score_desc" -> Posts.score to SortOrder.DESC
                "favcount", "favorites", "favorites_desc" -> Posts.favoritesCount to SortOrder.DESC
                "comments", "comment_bumped" -> Posts.commentCount to SortOrder.DESC
                else -> Posts.createdAt to SortOrder.DESC
            }

            val orderedRows = Posts.selectAll().where { Posts.id inList baseIds.toList() }
                .orderBy(orderBy.first, orderBy.second)
                .toList()
            val paged = orderedRows.drop((safePage - 1) * safeLimit).take(safeLimit)
            val userMap = loadUsers(paged.map { it[Posts.uploaderId].value }.distinct())
            val tagMap = loadPostTags(paged.map { it[Posts.id].value })

            PagedResponse(
                page = safePage,
                limit = safeLimit,
                total = total,
                items = paged.mapNotNull { row ->
                    userMap[row[Posts.uploaderId].value]?.let { row.toPostDto(it, tagMap[row[Posts.id].value].orEmpty()) }
                }
            )
        }
    }

    suspend fun addFavorite(postId: Long, userId: Long) {
        DatabaseFactory.dbQuery {
            Favorites.insertIgnore {
                it[Favorites.userId] = userId
                it[Favorites.postId] = postId
                it[Favorites.createdAt] = Instant.now()
            }
            recalcFavorites(postId)
        }
    }

    suspend fun removeFavorite(postId: Long, userId: Long) {
        DatabaseFactory.dbQuery {
            Favorites.deleteWhere { (Favorites.userId eq userId) and (Favorites.postId eq postId) }
            recalcFavorites(postId)
        }
    }

    suspend fun listFavorites(userId: Long): List<PostDto> = DatabaseFactory.dbQuery {
        val postIds = Favorites.selectAll().where { Favorites.userId eq userId }
            .orderBy(Favorites.createdAt, SortOrder.DESC)
            .map { it[Favorites.postId].value }
        if (postIds.isEmpty()) return@dbQuery emptyList()
        val rows = Posts.selectAll().where { Posts.id inList postIds }.toList().sortedBy { postIds.indexOf(it[Posts.id].value) }
        val userMap = loadUsers(rows.map { it[Posts.uploaderId].value }.distinct())
        val tagMap = loadPostTags(rows.map { it[Posts.id].value })
        rows.mapNotNull { row -> userMap[row[Posts.uploaderId].value]?.let { row.toPostDto(it, tagMap[row[Posts.id].value].orEmpty()) } }
    }

    suspend fun vote(postId: Long, userId: Long, score: Int) {
        require(score == 1 || score == -1) { "Score must be 1 or -1" }
        DatabaseFactory.dbQuery {
            PostVotes.deleteWhere { (PostVotes.userId eq userId) and (PostVotes.postId eq postId) }
            PostVotes.insert {
                it[PostVotes.userId] = userId
                it[PostVotes.postId] = postId
                it[PostVotes.score] = score
                it[PostVotes.createdAt] = Instant.now()
            }
            recalcScore(postId)
        }
    }

    suspend fun unvote(postId: Long, userId: Long) {
        DatabaseFactory.dbQuery {
            PostVotes.deleteWhere { (PostVotes.userId eq userId) and (PostVotes.postId eq postId) }
            recalcScore(postId)
        }
    }

    suspend fun listUploads(userId: Long, limit: Int): List<UploadDto> = DatabaseFactory.dbQuery {
        Uploads.selectAll()
            .where { Uploads.uploaderId eq userId }
            .orderBy(Uploads.createdAt, SortOrder.DESC)
            .limit(limit)
            .map {
                UploadDto(
                    id = it[Uploads.id].value,
                    postId = it[Uploads.postId].value,
                    uploaderId = it[Uploads.uploaderId].value,
                    fileName = it[Uploads.fileName],
                    source = it[Uploads.sourceUrl],
                    createdAt = it[Uploads.createdAt].toString(),
                )
            }
    }

    suspend fun listVersions(postId: Long): List<PostVersionDto> = DatabaseFactory.dbQuery {
        PostVersions.selectAll().where { PostVersions.postId eq postId }
            .orderBy(PostVersions.createdAt, SortOrder.DESC)
            .map {
                PostVersionDto(
                    id = it[PostVersions.id].value,
                    postId = it[PostVersions.postId].value,
                    updaterId = it[PostVersions.updaterId].value,
                    snapshotJson = it[PostVersions.snapshotJson],
                    createdAt = it[PostVersions.createdAt].toString(),
                )
            }
    }

    suspend fun getUploaderId(postId: Long): Long? = DatabaseFactory.dbQuery {
        Posts.selectAll().where { Posts.id eq postId }.singleOrNull()?.get(Posts.uploaderId)?.value
    }

    suspend fun setStatus(postId: Long, status: PostStatus, actorId: Long): PostDto? {
        DatabaseFactory.dbQuery {
            Posts.update({ Posts.id eq postId }) {
                it[Posts.status] = status.name
                it[Posts.updatedAt] = Instant.now()
            }
        }
        snapshot(postId, actorId)
        return getPost(postId)
    }

    suspend fun purgeExpiredDeletedPosts(retentionDays: Long = 30): Int {
        val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
        val files = DatabaseFactory.dbQuery {
            val rows = Posts.selectAll()
                .where { (Posts.status eq PostStatus.DELETED.name) and (Posts.updatedAt lessEq cutoff) }
                .toList()
                .filter { row ->
                    val postId = row[Posts.id].value
                    PostAppeals.selectAll()
                        .where { (PostAppeals.postId eq postId) and (PostAppeals.status eq "OPEN") }
                        .count() == 0L
                }

            rows.map { row ->
                DeletedFileSet(
                    id = row[Posts.id].value,
                    originalPath = row[Posts.originalPath],
                    previewPath = row[Posts.previewPath],
                )
            }.also { expired ->
                expired.forEach { item ->
                    Posts.deleteWhere { Posts.id eq item.id }
                }
            }
        }

        files.forEach { file -> storageService.deletePaths(file.originalPath, file.previewPath) }
        return files.size
    }

    suspend fun incrementCommentCount(postId: Long) = DatabaseFactory.dbQuery {
        val count = Comments.selectAll().where { (Comments.postId eq postId) and (Comments.isDeleted eq false) }.count().toInt()
        Posts.update({ Posts.id eq postId }) {
            it[Posts.commentCount] = count
            it[Posts.updatedAt] = Instant.now()
        }
    }

    private suspend fun attachTags(postId: Long, rawTagString: String) {
        val names = rawTagString.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tags = taxonomyRepository.ensureTags(names)
        val newTagIds = tags.map { it.id }
        val oldTagIds = DatabaseFactory.dbQuery {
            PostTags.selectAll().where { PostTags.postId eq postId }.map { it[PostTags.tagId].value }
        }
        DatabaseFactory.dbQuery {
            PostTags.deleteWhere { PostTags.postId eq postId }
            newTagIds.forEach { tagId ->
                PostTags.insertIgnore {
                    it[PostTags.postId] = postId
                    it[PostTags.tagId] = tagId
                }
            }
            Posts.update({ Posts.id eq postId }) {
                it[Posts.tagString] = tags.joinToString(" ") { tag -> tag.name }
                it[Posts.updatedAt] = Instant.now()
            }
        }
        taxonomyRepository.recountPosts((oldTagIds + newTagIds).distinct())
    }

    private suspend fun snapshot(postId: Long, actorId: Long) {
        DatabaseFactory.dbQuery {
            val post = Posts.selectAll().where { Posts.id eq postId }.singleOrNull() ?: return@dbQuery
            val tags = loadPostTags(listOf(postId))[postId].orEmpty()
            val snapshot = buildString {
                append("{" )
                append("\"id\":$postId,")
                append("\"rating\":\"")
                append(post[Posts.rating])
                append("\",")
                append("\"status\":\"")
                append(post[Posts.status])
                append("\",")
                append("\"source\":\"")
                append(post[Posts.sourceUrl] ?: "")
                append("\",")
                append("\"tags\":\"")
                append(tags.joinToString(" "))
                append("\"")
                append("}")
            }
            PostVersions.insert {
                it[PostVersions.postId] = postId
                it[PostVersions.updaterId] = actorId
                it[PostVersions.snapshotJson] = snapshot
                it[PostVersions.createdAt] = Instant.now()
            }
        }
    }

    private fun recalcFavorites(postId: Long) {
        val count = Favorites.selectAll().where { Favorites.postId eq postId }.count().toInt()
        Posts.update({ Posts.id eq postId }) {
            it[Posts.favoritesCount] = count
            it[Posts.updatedAt] = Instant.now()
        }
    }

    private fun recalcScore(postId: Long) {
        val total = PostVotes.selectAll().where { PostVotes.postId eq postId }.sumOf { it[PostVotes.score] }
        Posts.update({ Posts.id eq postId }) {
            it[Posts.score] = total
            it[Posts.updatedAt] = Instant.now()
        }
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

    private fun loadPostTags(postIds: List<Long>): Map<Long, List<String>> {
        if (postIds.isEmpty()) return emptyMap()
        return (PostTags innerJoin Tags).selectAll()
            .where { PostTags.postId inList postIds }
            .groupBy { it[PostTags.postId].value }
            .mapValues { (_, rows) -> rows.map { it[Tags.name] }.sorted() }
    }

    private fun ResultRow.toPostDto(uploader: UserDto, tags: List<String>): PostDto = PostDto(
        id = this[Posts.id].value,
        uploader = uploader,
        source = this[Posts.sourceUrl],
        rating = PostRating.valueOf(this[Posts.rating]),
        status = PostStatus.valueOf(this[Posts.status]),
        fileExt = this[Posts.fileExt],
        fileSize = this[Posts.fileSize],
        width = this[Posts.width],
        height = this[Posts.height],
        tagString = this[Posts.tagString],
        tags = tags,
        parentId = this[Posts.parentPostId]?.value,
        score = this[Posts.score],
        favoritesCount = this[Posts.favoritesCount],
        commentCount = this[Posts.commentCount],
        originalUrl = storageService.toPublicUrl(this[Posts.originalPath]),
        previewUrl = storageService.toPublicUrl(this[Posts.previewPath]),
        createdAt = this[Posts.createdAt].toString(),
        updatedAt = this[Posts.updatedAt].toString(),
    )
}
