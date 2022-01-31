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
        kotlinOptions.freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
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
