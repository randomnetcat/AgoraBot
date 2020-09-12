package org.randomcat.agorabot

import org.junit.jupiter.api.assertThrows
import org.randomcat.agorabot.util.withTempFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class WithTempFileTest {
    @Test
    fun `file is empty on creation`() {
        withTempFile { file ->
            assertTrue(Files.readAllBytes(file).isEmpty())
        }
    }

    @Test
    fun `returns block value`() {
        val value = 12
        assertEquals(value, withTempFile { value })
    }

    @Test
    fun `file does not exist after block end`() {
        val tempFilePath = withTempFile { it }
        assertFalse(Files.exists(tempFilePath))
    }

    @Test
    fun `propagates exception`() {
        val exception = Exception()

        assertSame(
            exception,
            assertThrows<Exception> { throw exception }
        )
    }

    @Test
    fun `file does not exist after block throw`() {
        lateinit var tempFilePath: Path

        try {
            withTempFile {
                tempFilePath = it
                throw Exception()
            }
        } catch (e: Exception) {
            /* ignored */
        }

        assertFalse(Files.exists(tempFilePath))
    }

    @Test
    fun `can delete tempFile in block`() {
        withTempFile { tempFile ->
            Files.delete(tempFile)
        }

        // This should cause no problems - if an exception is thrown, the test will fail
    }
}
