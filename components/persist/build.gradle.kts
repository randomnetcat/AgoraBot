plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.feature)
    implementation(projects.util.common)
    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.slf4j.api)
}
