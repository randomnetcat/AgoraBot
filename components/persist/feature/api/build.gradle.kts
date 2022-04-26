plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.feature)
    api(projects.components.persist.api)
}
