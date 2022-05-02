plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.components.versioningStorage.api)
    implementation(projects.core.feature)
}
