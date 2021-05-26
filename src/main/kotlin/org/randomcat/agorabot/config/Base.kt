package org.randomcat.agorabot.config

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readText
import java.nio.file.NoSuchFileException as NioNoSuchFileException

private val logger = LoggerFactory.getLogger("AgoraBotConfig")

class BadConfigException : Exception {
    constructor(message: String) : super(message)
}

fun <T> readConfigFromFile(path: Path, default: T, parseText: (String) -> T): T {
    val text = try {
        path.readText()
    } catch (e: NioNoSuchFileException) {
        logger.warn("Attempt to read config path $path that did not exist.")
        return default
    } catch (e: IOException) {
        logger.error("IOException when attempting to read config path $path", e)
        return default
    }

    return try {
        parseText(text)
    } catch (e: BadConfigException) {
        logger.error("Invalid configuration: ${e.message}")
        default
    } catch (e: Exception) {
        logger.error("Error while parsing config path $path", e)
        default
    }
}
