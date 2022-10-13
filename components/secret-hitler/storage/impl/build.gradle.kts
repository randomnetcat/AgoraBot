plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(projects.components.secretHitler.storage.api)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinUtils)
    implementation(projects.components.persist.api)
}
