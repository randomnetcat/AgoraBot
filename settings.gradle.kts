plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

rootProject.name = "AgoraBot"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":util")
include(":util:common")
include(":util:discord")
include(":util:irc")
include(":core:config")
include(":core:feature")
include(":core:command")
include(":base-command:api")
include(":base-command:requirements:discord")
include(":base-command:requirements:discord-ext")
include(":base-command:requirements:permissions")
include(":base-command:requirements:haltable")
include(":components:buttons")
include(":components:persist")
include(":components:permissions")
include(":components:guild-state")
include(":components:community-message")
include(":components:versioning-storage")
include(":components:secret-hitler:model")
include(":components:secret-hitler:context")
include(":components:secret-hitler:button-data")
include(":components:secret-hitler:storage:api")
include(":components:secret-hitler:storage:impl")
include(":components:secret-hitler:storage:feature:api")
include(":components:secret-hitler:storage:feature:impl")
include(":components:secret-hitler:handlers")
