import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.0"
}
group = "org.randomcat"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    implementation("net.dv8tion:JDA:4.2.0_198")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "13"
}
