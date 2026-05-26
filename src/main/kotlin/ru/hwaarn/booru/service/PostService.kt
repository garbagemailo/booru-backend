package ru.hwaarn.booru.service

import ru.hwaarn.booru.config.ModerationConfig
import ru.hwaarn.booru.config.UserSession
import ru.hwaarn.booru.model.PostStatus
import ru.hwaarn.booru.model.UploadMetadata
import ru.hwaarn.booru.repository.PostRepository
import ru.hwaarn.booru.util.StorageService

class PostService(
    private val postRepository: PostRepository,
    private val storageService: StorageService,
    private val moderationConfig: ModerationConfig,
) {
    suspend fun upload(user: UserSession, metadata: UploadMetadata, originalFileName: String?, bytes: ByteArray?) =
        when {
            bytes != null -> createOrReuse(user, metadata, storageService.storeUpload(originalFileName, bytes))
            metadata.source != null -> createOrReuse(user, metadata, storageService.storeFromSource(metadata.source))
            else -> error("Either file or source must be provided")
        }

    private suspend fun createOrReuse(user: UserSession, metadata: UploadMetadata, stored: StorageService.StoredFile) =
        postRepository.getPostBySha256(stored.sha256)?.also {
            storageService.delete(stored)
        } ?: postRepository.createPost(user.userId, metadata, stored, initialStatusFor(user))

    private fun initialStatusFor(user: UserSession): PostStatus {
        val trusted = user.role.name in setOf("JANITOR", "MODERATOR", "ADMIN")
        return if (moderationConfig.autoApproveMembers || trusted) PostStatus.ACTIVE else PostStatus.PENDING
    }
}
