import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "org.randomcat"
version = "1.0-SNAPSHOT"

allprojects {
    afterEvaluate {
        extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java)?.run {
            jvmToolchain {
                this as JavaToolchainSpec

                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }

        tasks.findByName("jar")?.run {
            this as Jar

            if (path != ":") {
                archiveBaseName.set("agorabot-" + path.removePrefix(":").replace(":", "-"))
            }
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.findByName("test")?.run {
        this as Test

        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

dependencies {
    // Updated with Kotlin version
    implementation(kotlin("reflect"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)

    // Other
    implementation(projects.util)
    implementation(projects.core.command)
    implementation(projects.core.config)
    implementation(projects.core.feature)
    implementation(projects.baseCommand.api)
    implementation(projects.baseCommand.requirements.discord)
    implementation(projects.baseCommand.requirements.discordExt)
    implementation(projects.baseCommand.requirements.permissions)
    implementation(projects.baseCommand.requirements.haltable)

    implementation(projects.components.persist.feature.api)
    runtimeOnly(projects.components.persist.feature.impl)

    implementation(projects.components.permissions.feature.api)
    runtimeOnly(projects.components.permissions.feature.impl)

    implementation(projects.components.guildState.feature.api)
    runtimeOnly(projects.components.guildState.feature.impl)

    implementation(projects.components.buttons.feature.api)
    runtimeOnly(projects.components.buttons.feature.impl)

    implementation(projects.components.communityMessage.feature.api)
    runtimeOnly(projects.components.communityMessage.feature.impl)

    implementation(projects.components.versioningStorage.feature.api)
    runtimeOnly(projects.components.versioningStorage.feature.impl)

    implementation(projects.components.secretHitler.model)
    implementation(projects.components.secretHitler.context)
    implementation(projects.components.secretHitler.buttonData)
    implementation(projects.components.secretHitler.storage.api)
    implementation(projects.components.secretHitler.storage.impl)
    implementation(projects.components.secretHitler.handlers)

    implementation(projects.components.secretHitler.storage.feature.api)
    runtimeOnly(projects.components.secretHitler.storage.feature.impl)

    implementation(libs.kotlinx.collectionsImmutable)
    implementation(libs.jda)
    implementation(libs.kitteh)
    implementation(libs.clikt)
    implementation(libs.kotlinUtils)
    implementation(libs.jakarta.json.api)
    implementation(libs.classgraph)

    runtimeOnly(libs.jakarta.json.runtime)
    runtimeOnly(libs.slf4j.simple)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit)
}

application {
    applicationName = "AgoraBot"
    mainClass.set("org.randomcat.agorabot.MainKt")
}
