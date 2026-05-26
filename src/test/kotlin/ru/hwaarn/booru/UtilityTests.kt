package ru.hwaarn.booru

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import ru.hwaarn.booru.config.StorageConfig
import ru.hwaarn.booru.util.StorageService

class UtilityTests {
    @Test
    fun `storage service converts file path to public url`() {
        val root = Files.createTempDirectory("booru-storage-test").toFile()
        val dayDir = root.resolve("2026-04-03").apply { mkdirs() }
        val original = dayDir.resolve("sample.png").apply { writeText("demo") }
        val service = StorageService(
            StorageConfig(
                uploadRoot = root.absolutePath,
                publicBaseUrl = "http://localhost:8080",
            ),
        )

        assertEquals(
            "http://localhost:8080/media/2026-04-03/sample.png",
            service.toPublicUrl(original.absolutePath),
        )
    }

    @Test
    fun `storeFromSource rejects unsupported schemes`() {
        val root = Files.createTempDirectory("booru-storage-test").toFile()
        val service = StorageService(
            StorageConfig(
                uploadRoot = root.absolutePath,
                publicBaseUrl = "http://localhost:8080",
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            service.storeFromSource("ftp://example.com/image.png")
        }
    }
}
