plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.feature)
    api(projects.components.buttons.api)
}
