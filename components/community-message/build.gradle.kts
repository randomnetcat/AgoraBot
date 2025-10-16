plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.core.feature)
    implementation(projects.components.persist)
    implementation(projects.util.common)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
}
