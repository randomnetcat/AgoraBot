plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.core.config)
    implementation(projects.util.common)
}
