plugins {
    java
    kotlin("jvm") version "1.4.0"
}

group = "org.agoranomic"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    fun kotlinx(module: String, version: String? = null): Any =
        "org.jetbrains.kotlinx:kotlinx-$module${version?.let { ":$version" } ?: ""}"

    implementation(kotlin("reflect"))
    implementation(kotlinx("collections-immutable", "0.3"))
    implementation(kotlinx("serialization-runtime", "0.13.0"))
    implementation("commons-cli:commons-cli:1.4")
    implementation("io.github.classgraph:classgraph:4.8.46")
    implementation("io.github.random-internet-cat:kotlin-utils:1.1.0")

    // Dependencies for statistics
    implementation("org.jetbrains.lets-plot:lets-plot-batik:2.0.0")
    implementation("org.jetbrains.lets-plot:lets-plot-image-export:2.0.0")
    api("org.jetbrains.lets-plot:lets-plot-common:2.0.0")
    api("org.jetbrains.lets-plot-kotlin:lets-plot-kotlin-api:1.2.0")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.5.2")
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.create<Jar>("fatJar") {
    manifest {
        attributes["Implementation-Title"] = "Agora Assessor"
        attributes["Implementation-Version"] = project.version
        attributes["Main-Class"] = "org.agoranomic.assessor.cli.RunnerKt"
    }

    archiveFileName.set("assessor.jar")

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks["jar"] as CopySpec)
}

tasks.compileKotlin {
    sourceCompatibility = "11"
    kotlinOptions.jvmTarget = "11"

    kotlinOptions {
        freeCompilerArgs += listOf("-Xinline-classes")
    }
}

tasks.compileTestKotlin {
    kotlinOptions.jvmTarget = "11"
}
