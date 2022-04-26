plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.command)
    implementation(projects.baseCommand.api)
    implementation(projects.components.permissions.api)
    implementation(libs.jda)
    implementation(libs.kotlinx.collectionsImmutable)
}
