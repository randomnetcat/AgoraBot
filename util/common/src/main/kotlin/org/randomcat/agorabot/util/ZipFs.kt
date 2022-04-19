package org.randomcat.agorabot.util

import java.nio.file.spi.FileSystemProvider

private val ZIP_FILE_SYSTEM_PROVIDER = FileSystemProvider.installedProviders().single {
    it.scheme.equals("jar", ignoreCase = true)
}

fun zipFileSystemProvider(): FileSystemProvider = ZIP_FILE_SYSTEM_PROVIDER
