plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinUtils)
}
