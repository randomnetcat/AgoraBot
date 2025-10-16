plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.components.secretHitler.model)
    api(projects.components.buttons)
    implementation(projects.util.discord)
    api(libs.jda)
}
