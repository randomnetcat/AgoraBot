plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.feature)
    implementation(projects.components.communityMessage.api)
    implementation(projects.components.communityMessage.impl)
    implementation(projects.components.communityMessage.feature.api)
    implementation(projects.components.persist.feature.api)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.kotlinx.serialization.json)
}
