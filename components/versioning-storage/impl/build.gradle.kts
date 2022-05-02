plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.components.versioningStorage.api)
    implementation(projects.util.common)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
}
