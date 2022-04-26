package org.randomcat.agorabot.irc

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.feature.auth.SaslEcdsaNist256PChallenge
import org.kitteh.irc.client.library.feature.sts.StsPropertiesStorageManager
import org.kitteh.irc.client.library.feature.sts.StsStorageManager
import java.nio.file.Path


/**
 * In order to provide more information in the name than just "Client".
 */
typealias IrcClient = Client

private fun doCreateIrcClient(serverConfig: IrcServerConfig, stsStorageManager: StsStorageManager): IrcClient {
    return Client
        .builder()
        .server()
        .host(serverConfig.host)
        .port(
            serverConfig.port,
            if (serverConfig.serverIsSecure) Client.Builder.Server.SecurityType.SECURE else Client.Builder.Server.SecurityType.INSECURE
        )
        .then()
        .nick(serverConfig.userNickname)
        .management()
        .stsStorageManager(stsStorageManager)
        .then()
        .build()
        .also { client ->
            @Suppress("UNUSED_VARIABLE")
            val ensureExhaustive: Any = when (serverConfig.authentication) {
                is IrcServerAuthentication.EcdsaPrivateKey -> {
                    client.authManager.addProtocol(
                        SaslEcdsaNist256PChallenge(
                            client,
                            serverConfig.userNickname,
                            serverConfig.authentication.key,
                        ),
                    )
                }

                null -> {
                }
            }
        }
        .also {
            it.connect()
        }
}

@JvmInline
value class IrcServerName(val raw: String)

data class IrcClientMap(private val clientsByName: ImmutableMap<IrcServerName, IrcClient>) {
    constructor(clientsByName: Map<IrcServerName, IrcClient>) : this(clientsByName.toImmutableMap())

    val clients: Iterable<IrcClient>
        get() = clientsByName.values

    fun getByName(name: IrcServerName): IrcClient {
        return clientsByName.getValue(name)
    }
}

fun createIrcClients(ircSetupConfig: IrcSetupConfig, ircDir: Path): IrcClientMap {
    val serverListConfig = ircSetupConfig.serverListConfig

    val storageManager = StsPropertiesStorageManager(ircDir.resolve("kicl_sts_storage"))

    return IrcClientMap(
        serverListConfig.names.associateWith { name ->
            doCreateIrcClient(serverListConfig.getByName(name), storageManager)
        },
    )
}
