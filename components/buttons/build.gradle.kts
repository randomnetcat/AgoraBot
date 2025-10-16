plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(kotlin("reflect"))

    implementation(projects.util.common)
    implementation(projects.core.feature)
    implementation(projects.components.persist)
    implementation(projects.components.versioningStorage)

    implementation(libs.jda)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinUtils)
    implementation(libs.slf4j.api)
}
