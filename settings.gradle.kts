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
include(":base-command:requirements:discord-ext")
include(":base-command:requirements:permissions")
include(":base-command:requirements:haltable")
include(":components:buttons:api")
include(":components:buttons:impl")
include(":components:buttons:feature:api")
include(":components:buttons:feature:impl")
include(":components:persist:api")
include(":components:persist:impl")
include(":components:persist:feature:api")
include(":components:persist:feature:impl")
include(":components:permissions:api")
include(":components:permissions:impl")
include(":components:permissions:feature:api")
include(":components:permissions:feature:impl")
include(":components:guild-state:api")
include(":components:guild-state:impl")
include(":components:guild-state:feature:api")
include(":components:guild-state:feature:impl")
include(":components:community-message:api")
include(":components:community-message:impl")
include(":components:community-message:feature:api")
include(":components:community-message:feature:impl")
include(":components:versioning-storage:api")
include(":components:versioning-storage:impl")
include(":components:versioning-storage:feature:api")
include(":components:versioning-storage:feature:impl")
include(":components:secret-hitler:model")
