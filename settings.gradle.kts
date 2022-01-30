rootProject.name = "AgoraBot"

enableFeaturePreview("VERSION_CATALOGS")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":util")
include(":util:common")
include(":util:discord")
include(":util:irc")
include(":core:config")
include(":core:feature")
include(":core:command")
