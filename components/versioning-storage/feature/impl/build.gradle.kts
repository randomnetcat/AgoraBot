plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.components.versioningStorage.feature.api)
    implementation(projects.core.feature)
    implementation(projects.components.versioningStorage.impl)
}
