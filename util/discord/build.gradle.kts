plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.util.common)
    api(libs.jda)
    implementation(libs.kotlinx.coroutines)
}
