plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.components.persist.api)
    implementation(projects.components.communityMessage.api)
    implementation(projects.util.common)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
}
