plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.components.secretHitler.model)
    api(projects.components.secretHitler.context)
    api(projects.components.secretHitler.storage.api)
    implementation(projects.components.secretHitler.buttonData)
    implementation(projects.util.discord)
}
