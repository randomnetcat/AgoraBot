plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.util.common)
    implementation(projects.core.feature)
    implementation(projects.components.secretHitler.storage.feature.api)
    implementation(projects.components.secretHitler.storage.impl)
    implementation(projects.components.persist.feature.api)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
}
