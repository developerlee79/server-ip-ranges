plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    `maven-publish`
}

group = "com.devlee79"
version = "1.1.0"

repositories {
    mavenCentral()
}

dependencies {
    val ktorVersion = "2.3.4"
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-java:$ktorVersion")

    val serializationVersion = "1.6.0"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform {
        excludeTags("network")
    }
}

tasks.register<Test>("updateRangeFiles") {
    description = "Fetches live provider IP ranges and regenerates range/*.json"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("network")
    }
    outputs.upToDateWhen { false }
}

kotlin {
    jvmToolchain(11)
}

/*
* Packs range/<provider>/ip-range.json into compact binary lookup tables that are bundled in
* the jar, so RangeFileUtil's classpath fallback works when the library is consumed as a
* dependency. Generated at build time rather than committed: a clean checkout produces the
* data, and only the human-readable JSON lives in git.
*
* configurations.runtimeClasspath is used instead of sourceSets.main.runtimeClasspath so the
* task does not depend on processResources, which in turn consumes this task's output.
*/
val packedRangeDir = layout.buildDirectory.dir("generated/range")

val packRangeData by tasks.registering(JavaExec::class) {
    description = "Packs range/*/ip-range.json into binary lookup tables"
    group = "build"
    mainClass.set("com.devlee.ipranges.core.io.RangePackerKt")
    classpath = sourceSets.main.get().output.classesDirs + configurations.runtimeClasspath.get()
    args(file("range").absolutePath, packedRangeDir.get().asFile.absolutePath)
    inputs.files(fileTree("range") { include("*/ip-range.json") })
    outputs.dir(packedRangeDir)
}

sourceSets.main {
    resources.srcDir(packRangeData)
}

/*
* Required for JitPack: it publishes whatever the maven publication produces.
* Intentionally no developers/scm metadata — keeps personal info out of the POM.
*/
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
