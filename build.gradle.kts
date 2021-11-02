import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.0"
    kotlin("plugin.serialization") version "1.5.0"
    application
}

group = "org.randomcat"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    maven {
        name = "m2-dv8tion"
        url = uri("https://m2.dv8tion.net/releases")
    }
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.4")
    implementation("net.dv8tion:JDA:4.3.0_339")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
    implementation("org.kitteh.irc:client-lib:8.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.0")
    implementation("com.github.ajalt.clikt:clikt:3.1.0")
    implementation("org.randomcat:kotlin-utils:2.0.1")
    implementation("jakarta.json:jakarta.json-api:2.0.1")
    runtimeOnly("org.glassfish:jakarta.json:2.0.1")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.5.2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "13"

    kotlinOptions.freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

application {
    applicationName = "AgoraBot"
    mainClass.set("org.randomcat.agorabot.MainKt")
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}
