package org.randomcat.agorabot.secrethitler.storage.api

interface SecretHitlerImpersonationMap {
    fun currentNameForId(userId: String): String?
    fun dmUserIdsForName(name: String): Set<String>?
}

interface SecretHitlerMutableImpersonationMap : SecretHitlerImpersonationMap {
    fun setNameForId(userId: String, newName: String)
    fun clearNameForId(userId: String)

    fun addDmUserIdForName(name: String, userId: String)
    fun clearDmUsersForName(name: String)
}
