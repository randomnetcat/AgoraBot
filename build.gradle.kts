import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.0"
    kotlin("plugin.serialization") version "1.4.0"
}
group = "org.randomcat"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.3")
    implementation("net.dv8tion:JDA:4.2.0_198")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.0.0-RC")
    implementation("org.kitteh.irc:client-lib:7.3.0")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.5.2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "13"

    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.create<Jar>("fatJar") {
    manifest {
        attributes["Implementation-Title"] = "Agora Discord Bot"
        attributes["Implementation-Version"] = project.version
        attributes["Main-Class"] = "org.randomcat.agorabot.MainKt"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    archiveFileName.set("agorabot.jar")

    from(
        configurations
            .runtimeClasspath
            .get()
            .files
            .filter { !it.path.endsWith(".pom") } // Apparently the task breaks horribly on pom files /shrug
            .map { if (it.isDirectory) it else zipTree(it) }
    )

    with(tasks["jar"] as CopySpec)
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}
val compileKotlin: KotlinCompile by tasks

compileKotlin.kotlinOptions {
    freeCompilerArgs = listOf("-Xinline-classes")
}
