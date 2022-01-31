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
include(":base-command:api")
include(":base-command:requirements:discord")
include(":components:persist:api")
include(":components:persist:impl")
include(":components:persist:feature:api")
include(":components:persist:feature:impl")
