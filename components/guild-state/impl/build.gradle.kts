plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.components.persist.api)
    implementation(projects.components.guildState.api)
    implementation(projects.util.common)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
}
