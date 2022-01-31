plugins {
    kotlin("jvm")
}

dependencies {
    implementation(projects.core.feature)
    implementation(projects.components.persist.api)
    implementation(projects.components.persist.impl)
    implementation(projects.components.persist.feature.api)
}
