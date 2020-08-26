package org.randomcat.agorabot

import org.randomcat.agorabot.commands.UnparsedCommandArgs
import kotlin.test.Test
import kotlin.test.assertEquals

class UnparsedCommandArgsTest {
    @Test
    fun `drop works`() {
        val args = UnparsedCommandArgs(listOf("A", "B", "C"))

        assertEquals(
            UnparsedCommandArgs(listOf("C")),
            args.drop(2)
        )
    }

    @Test
    fun `excess drop is empty`() {
        val args = UnparsedCommandArgs(listOf("A", "B", "C"))

        assertEquals(
            UnparsedCommandArgs(emptyList()),
            args.drop(4)
        )
    }

    @Test
    fun `tail is drop first`() {
        val args = UnparsedCommandArgs(listOf("A", "B", "C"))

        assertEquals(
            UnparsedCommandArgs(listOf("B", "C")),
            args.tail()
        )
    }

    @Test
    fun `tail of empty is empty`() {
        assertEquals(
            UnparsedCommandArgs(emptyList()),
            UnparsedCommandArgs(emptyList()).tail()
        )
    }
}
