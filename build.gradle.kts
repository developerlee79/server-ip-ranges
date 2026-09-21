plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    `maven-publish`
}

group = "com.devlee79"
version = "2.0.0"

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
    /*
    * Points the library at this checkout, so refreshed ranges are written here and read back
    * from the JSON files. The default test task deliberately leaves the property unset, which
    * is what makes the suite exercise the packed tables a consumer actually gets.
    */
    systemProperty("ipranges.dataDir", projectDir.absolutePath)
    outputs.upToDateWhen { false }
}

kotlin {
    jvmToolchain(11)
}

/*
* Builds the release assets: one packed <provider>.bin per provider plus version.json.
* CI runs updateRangeFiles then this, and attaches the output to a release — no range data is
* committed, and none ships in the jar. Feed it the previous release's version.json so a
* collapsed provider fails the build instead of being published.
*/
val releaseDataDir = layout.buildDirectory.dir("release-data")

tasks.register<JavaExec>("packReleaseData") {
    description = "Packs range/*/ip-range.json into the published release assets"
    group = "build"
    mainClass.set("com.devlee.ipranges.core.io.RangePackerKt")
    classpath = sourceSets.main.get().output.classesDirs + configurations.runtimeClasspath.get()

    val previousManifest = providers.gradleProperty("previousVersionJson")
    argumentProviders.add {
        listOfNotNull(
            projectDir.absolutePath,
            releaseDataDir.get().asFile.absolutePath,
            previousManifest.orNull
        )
    }

    inputs.files(fileTree("range") { include("*/ip-range.json") })
    /*
    * Declared as an input so swapping the comparison manifest re-runs the task: the shrink
    * guard is the whole point of passing one, and an up-to-date skip would silently bypass it.
    */
    inputs.property("previousVersionJson", previousManifest).optional(true)
    outputs.dir(releaseDataDir)
}

/*
* The provider list is configuration the parsers read at runtime, so it ships in the jar as
* well; without it ProviderFileUtil only resolves inside a repo checkout. Namespaced under
* ipranges/ so it cannot collide with a consumer resource of the same name.
*/
tasks.processResources {
    from("provider-info.json") { into("ipranges") }
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
