plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.util)
    implementation(libs.jda)
    implementation(libs.kitteh)
    implementation(libs.kotlinx.collectionsImmutable)
}
