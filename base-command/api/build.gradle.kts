plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.command)
    implementation(libs.jda)
    implementation(libs.kotlinx.collectionsImmutable)
}
