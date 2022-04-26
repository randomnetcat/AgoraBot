plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.command)
    implementation(projects.util.discord)
    implementation(projects.baseCommand.api)
    implementation(libs.jda)
    implementation(libs.kotlinx.collectionsImmutable)
}
