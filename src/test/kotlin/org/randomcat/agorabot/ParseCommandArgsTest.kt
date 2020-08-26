package org.randomcat.agorabot

import org.randomcat.agorabot.commands.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ParseCommandArgsTest {
    private object EmptyArgumentError

    private object EchoCommandArgumentParser : CommandArgumentParser<String, EmptyArgumentError> {
        override fun parse(arguments: UnparsedCommandArgs): CommandArgumentParseResult<String, EmptyArgumentError> {
            val args = arguments.args

            if (args.isEmpty()) return CommandArgumentParseResult.Failure(EmptyArgumentError)
            return CommandArgumentParseResult.Success(args.first(), UnparsedCommandArgs(args.drop(1)))
        }
    }

    @Test
    fun `all success`() {
        val argList = listOf("this", "is", "a", "test")
        val args = UnparsedCommandArgs(argList)

        assertEquals(
            CommandArgumentParseResult.Success(argList, UnparsedCommandArgs(emptyList())),
            parseCommandArgs(
                listOf(
                    EchoCommandArgumentParser,
                    EchoCommandArgumentParser,
                    EchoCommandArgumentParser,
                    EchoCommandArgumentParser,
                ),
                args
            )
        )
    }

    @Test
    fun `unparsed arg`() {
        val argList = listOf("this", "is", "a", "test")
        val args = UnparsedCommandArgs(argList)

        assertEquals(
            CommandArgumentParseResult.Success(argList.dropLast(1), UnparsedCommandArgs(listOf(argList.last()))),
            parseCommandArgs(
                listOf(
                    EchoCommandArgumentParser,
                    EchoCommandArgumentParser,
                    EchoCommandArgumentParser,
                ),
                args
            )
        )
    }

    @Test
    fun `with error`() {
        val argList = listOf("this", "is", "a", "test")
        val args = UnparsedCommandArgs(argList)

        assertEquals(
            CommandArgumentParseResult.Failure(SequentialArgumentParseFailure(index = 4, EmptyArgumentError)),
            parseCommandArgs(
                listOf(
                    EchoCommandArgumentParser,
                    EchoCommandArgumentParser,
                    EchoCommandArgumentParser,
                    EchoCommandArgumentParser,
                    EchoCommandArgumentParser,
                ),
                args
            )
        )
    }
}
