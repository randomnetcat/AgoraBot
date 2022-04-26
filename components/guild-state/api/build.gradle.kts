plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.util.discord)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
}
