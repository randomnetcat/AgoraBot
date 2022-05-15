plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(projects.components.buttons.api)
    api(projects.components.secretHitler.model)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinUtils)
}
