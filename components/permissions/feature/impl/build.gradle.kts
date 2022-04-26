plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.feature)
    implementation(projects.components.permissions.api)
    implementation(projects.components.permissions.impl)
    implementation(projects.components.permissions.feature.api)
    implementation(projects.components.persist.feature.api)
    implementation(libs.slf4j.api)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
}
