package com.example.mycards

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for [ImageStorage].
 *
 * Uses a real device/emulator Context so actual file I/O can be exercised.
 * The images/ directory is cleaned up after every test to keep them isolated.
 */
@RunWith(AndroidJUnit4::class)
class ImageStorageTest {

    private lateinit var context: Context
    private lateinit var imagesDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        imagesDir = File(context.filesDir, "images")
    }

    @After
    fun tearDown() {
        // Remove every file created by the tests
        imagesDir.deleteRecursively()
    }

    // ── File creation ────────────────────────────────────────────────────────

    @Test
    fun copyToAppStorage_createsFileAtReturnedPath() = runTest {
        val source = createTempSource("some image bytes")

        val path = ImageStorage.copyToAppStorage(context, Uri.fromFile(source))

        assertTrue("File must exist at the returned path", File(path).exists())
    }

    @Test
    fun copyToAppStorage_fileIsInsideImagesSubdirectory() = runTest {
        val source = createTempSource("data")

        val path = ImageStorage.copyToAppStorage(context, Uri.fromFile(source))

        assertTrue(
            "Result must be inside filesDir/images/",
            path.startsWith(imagesDir.absolutePath)
        )
    }

    @Test
    fun copyToAppStorage_createsImagesDirWhenItDoesNotExist() = runTest {
        imagesDir.deleteRecursively()
        assertFalse("Precondition: images/ must not exist yet", imagesDir.exists())

        ImageStorage.copyToAppStorage(context, Uri.fromFile(createTempSource("data")))

        assertTrue("images/ directory should be created automatically", imagesDir.exists())
    }

    // ── Content correctness ──────────────────────────────────────────────────

    @Test
    fun copyToAppStorage_fileUri_contentMatchesSource() = runTest {
        val content = "FAKE_JPEG_BYTES_12345"
        val source = createTempSource(content)

        val path = ImageStorage.copyToAppStorage(context, Uri.fromFile(source))

        assertEquals(
            "Copied file content must be identical to the source",
            content,
            File(path).readText()
        )
    }

    @Test
    fun copyToAppStorage_emptyFile_producesEmptyDestination() = runTest {
        val source = createTempSource("")

        val path = ImageStorage.copyToAppStorage(context, Uri.fromFile(source))

        assertEquals("Copying an empty file should produce an empty destination", 0L, File(path).length())
    }

    // ── Uniqueness ───────────────────────────────────────────────────────────

    @Test
    fun copyToAppStorage_calledTwiceWithSameSource_returnsDifferentPaths() = runTest {
        val source = createTempSource("data")
        val uri = Uri.fromFile(source)

        val path1 = ImageStorage.copyToAppStorage(context, uri)
        val path2 = ImageStorage.copyToAppStorage(context, uri)

        assertNotEquals(
            "Each call should produce a unique destination file (UUID-based name)",
            path1, path2
        )
    }

    @Test
    fun copyToAppStorage_calledTwice_bothFilesExist() = runTest {
        val source = createTempSource("data")
        val uri = Uri.fromFile(source)

        val path1 = ImageStorage.copyToAppStorage(context, uri)
        val path2 = ImageStorage.copyToAppStorage(context, uri)

        assertTrue("First file must still exist after second call",  File(path1).exists())
        assertTrue("Second file must exist", File(path2).exists())
    }

    // ── deleteFromAppStorage ─────────────────────────────────────────────────

    @Test
    fun deleteFromAppStorage_fileUri_removesFileFromDisk() = runTest {
        val source = createTempSource("data to delete")
        val path = ImageStorage.copyToAppStorage(context, Uri.fromFile(source))
        assertTrue("Precondition: file must exist before delete", File(path).exists())

        ImageStorage.deleteFromAppStorage(Uri.fromFile(File(path)).toString())

        assertFalse("File should no longer exist after deleteFromAppStorage", File(path).exists())
    }

    @Test
    fun deleteFromAppStorage_nullUri_doesNotThrow() = runTest {
        // Should complete silently without any exception
        ImageStorage.deleteFromAppStorage(null)
    }

    @Test
    fun deleteFromAppStorage_contentUri_isIgnoredAndDoesNotThrow() = runTest {
        // Content URIs point to files we don't own — should be a no-op
        ImageStorage.deleteFromAppStorage("content://media/external/images/media/42")
    }

    @Test
    fun deleteFromAppStorage_otherFilesInDirAreUnaffected() = runTest {
        val source = createTempSource("shared source")
        val uri = Uri.fromFile(source)
        val path1 = ImageStorage.copyToAppStorage(context, uri)
        val path2 = ImageStorage.copyToAppStorage(context, uri)

        ImageStorage.deleteFromAppStorage(Uri.fromFile(File(path1)).toString())

        assertFalse("path1 should be deleted", File(path1).exists())
        assertTrue("path2 should be untouched",  File(path2).exists())
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Creates a temp file in the app's cache dir with the given text content. */
    private fun createTempSource(content: String): File =
        File.createTempFile("test_src_", ".jpg", context.cacheDir)
            .also { it.writeText(content) }
}
