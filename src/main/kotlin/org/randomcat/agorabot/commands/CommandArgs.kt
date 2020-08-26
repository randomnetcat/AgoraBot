package org.randomcat.agorabot.commands

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class UnparsedCommandArgs(val args: ImmutableList<String>) {
    constructor(args: List<String>) : this(args.toImmutableList())

    fun drop(n: Int) = UnparsedCommandArgs(args.drop(n))
    fun tail() = drop(1)
}

object CommandArgs0
data class CommandArgs1<A>(val first: A)
data class CommandArgs2<A, B>(val first: A, val second: B)
data class CommandArgs3<A, B, C>(val first: A, val second: B, val third: C)
data class CommandArgs4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
