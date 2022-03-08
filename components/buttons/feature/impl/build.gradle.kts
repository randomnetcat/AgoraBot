plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("reflect"))

    implementation(projects.core.feature)
    implementation(projects.components.buttons.api)
    implementation(projects.components.buttons.impl)
    implementation(projects.components.buttons.feature.api)
    implementation(projects.components.persist.feature.api)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
}
