package org.randomcat.agorabot.versioning_storage.api

interface VersioningStorage {
    fun versionFor(component: String): String?
    fun setVersion(component: String, version: String)
}
