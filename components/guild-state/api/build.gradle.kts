plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.util.discord)
    implementation(libs.kotlinx.serialization.json)
}
