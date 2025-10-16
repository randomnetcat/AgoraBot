plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.command)
    implementation(projects.baseCommand.api)
    implementation(projects.baseCommand.requirements.discord)
    implementation(projects.baseCommand.requirements.permissions)
    implementation(projects.components.buttons)
    implementation(projects.components.guildState)
    implementation(libs.jda)
    implementation(libs.kotlinx.collectionsImmutable)
}
