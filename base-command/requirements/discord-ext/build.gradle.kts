plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.command)
    implementation(projects.baseCommand.api)
    implementation(projects.baseCommand.requirements.discord)
    implementation(projects.baseCommand.requirements.permissions)
    implementation(projects.components.buttons.api)
    implementation(projects.components.guildState.api)
    implementation(libs.jda)
    implementation(libs.kotlinx.collectionsImmutable)
}
