plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.feature)
    implementation(projects.components.persist)
    implementation(projects.util.common)
    implementation(projects.util.discord)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
}
