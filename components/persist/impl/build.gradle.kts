plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.components.persist.api)
    implementation(libs.slf4j.api)
    implementation(projects.core.feature)
}
