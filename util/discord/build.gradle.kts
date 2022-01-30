plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.util.common)
    implementation(libs.jda)
    implementation(libs.kotlinx.coroutines)
}
