plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(projects.components.secretHitler.storage.api)
    implementation(libs.kotlinx.serialization.json)
    implementation(projects.components.persist.api)
}
