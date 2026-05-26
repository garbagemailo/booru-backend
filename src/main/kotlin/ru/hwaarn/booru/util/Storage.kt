package ru.hwaarn.booru.util

import net.coobird.thumbnailator.Thumbnails
import ru.hwaarn.booru.config.StorageConfig
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.time.Instant
import javax.imageio.ImageIO

class StorageService(private val config: StorageConfig) {
    private val root = File(config.uploadRoot).apply { mkdirs() }

    data class StoredFile(
        val originalPath: String,
        val previewPath: String,
        val ext: String,
        val fileSize: Long,
        val width: Int,
        val height: Int,
        val md5: String,
        val sha256: String,
        val originalFileName: String,
    )

    fun storeUpload(originalName: String?, bytes: ByteArray): StoredFile {
        val ext = originalName?.substringAfterLast('.', "bin")?.lowercase() ?: "bin"
        val timestamp = Instant.now().epochSecond
        val sha256 = Digests.sha256(bytes)
        val md5 = Digests.md5(bytes)
        val dir = File(root, "${Instant.now().toString().substring(0, 10)}").apply { mkdirs() }
        val original = File(dir, "$timestamp-$sha256.$ext")
        original.writeBytes(bytes)

        val preview = File(dir, "$timestamp-$sha256-preview.jpg")
        val image = runCatching { ImageIO.read(original) }.getOrNull()
        val width = image?.width ?: 0
        val height = image?.height ?: 0
        if (image != null) {
            runCatching {
                Thumbnails.of(original)
                    .size(400, 400)
                    .outputFormat("jpg")
                    .toFile(preview)
            }.getOrElse {
                original.copyTo(preview, overwrite = true)
            }
        } else {
            original.copyTo(preview, overwrite = true)
        }

        return StoredFile(
            originalPath = original.absolutePath,
            previewPath = preview.absolutePath,
            ext = ext,
            fileSize = original.length(),
            width = width,
            height = height,
            md5 = md5,
            sha256 = sha256,
            originalFileName = originalName ?: original.name,
        )
    }

    fun storeFromSource(source: String): StoredFile {
        val url = URL(source)
        require(url.protocol in setOf("http", "https")) { "Only http/https sources are supported" }
        val bytes = url.openStream().use { it.readBytes() }
        val originalName = source.substringAfterLast('/').substringBefore('?').ifBlank { "remote.bin" }
        return storeUpload(originalName, bytes)
    }

    fun delete(storedFile: StoredFile) {
        deletePaths(storedFile.originalPath, storedFile.previewPath)
    }

    fun deletePaths(originalPath: String, previewPath: String) {
        runCatching { File(originalPath).delete() }
        runCatching { File(previewPath).delete() }
    }

    fun toPublicUrl(path: String): String {
        val file = File(path)
        val relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
        return config.publicBaseUrl.trimEnd('/') + "/media/" + relative
    }
}
