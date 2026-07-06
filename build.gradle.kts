plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
}

group = "com.devlee79"
version = "1.0"

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
* Bundle the generated range data into the jar so RangeFileUtil's classpath
* fallback works when the library is consumed as a dependency.
*/
sourceSets.main {
    resources.srcDir("range")
}

tasks.processResources {
    exclude("**/ServiceTags_*.json")
}
