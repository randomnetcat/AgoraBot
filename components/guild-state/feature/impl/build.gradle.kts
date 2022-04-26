plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.feature)
    implementation(projects.components.guildState.api)
    implementation(projects.components.guildState.impl)
    implementation(projects.components.guildState.feature.api)
    implementation(projects.components.persist.feature.api)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
}
