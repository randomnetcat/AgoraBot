package org.randomcat.agorabot

import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.listener.CommandParseResult
import org.randomcat.agorabot.listener.parsePrefixCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParsePrefixCommandTest {
    private fun assertIgnored(result: CommandParseResult) {
        assertEquals(CommandParseResult.Ignore, result)
    }

    private fun assertInvocation(expected: CommandInvocation, result: CommandParseResult) {
        assertTrue(result is CommandParseResult.Invocation, "expected invocation, got $result")
        assertEquals(expected, result.invocation)
    }

    @Test
    fun `empty prefix`() {
        assertFailsWith<IllegalArgumentException> {
            parsePrefixCommand(prefix = "", "some message")
        }
    }

    @Test
    fun `no prefix`() {
        assertIgnored(parsePrefixCommand(prefix = ".", "no prefix here!"))
    }

    @Test
    fun `only prefix`() {
        assertIgnored(parsePrefixCommand(prefix = ".", message = "."))
    }

    @Test
    fun `simple`() {
        assertInvocation(CommandInvocation("test", listOf()), parsePrefixCommand(".", ".test"))
    }

    @Test
    fun `simple with hard args`() {
        assertInvocation(
            CommandInvocation("test", listOf("arg1", "arg the second")),
            parsePrefixCommand(".", ".test ar\\g1 \"arg the second\"")
        )
    }
}
