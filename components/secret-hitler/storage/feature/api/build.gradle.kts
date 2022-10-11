plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.components.secretHitler.storage.api)
    implementation(projects.core.feature)
}
