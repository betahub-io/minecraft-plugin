plugins {
    java
    alias(libs.plugins.run.paper)
}

repositories {
    maven("https://repo.viaversion.com")
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.viaversion.api)
    implementation(project(":core"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.jar {
    archiveBaseName = "BetaHubBugReporter"
    // Bundle the core module; Paper provides everything else (Adventure, Gson, Brigadier).
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // No server internals are used, so Paper does not need to remap the jar on startup.
    manifest { attributes("paperweight-mappings-namespace" to "mojang") }
}


// Local test servers (run-paper). `./gradlew :paper:runServer` starts Paper 1.21.11 on Java 21,
// `./gradlew :paper:runServer26` starts Paper 26.3 on Java 25 (downloaded by Gradle if missing).
val oldestSupportedVersion = "1.21.11"
val newestTestedVersion = "26.3"

tasks.runServer {
    minecraftVersion(oldestSupportedVersion)
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    runDirectory = rootProject.layout.projectDirectory.dir("run/$oldestSupportedVersion")
    jvmArgs("-Dcom.mojang.eula.agree=true")
}

tasks.register<xyz.jpenilla.runpaper.task.RunServer>("runServer26") {
    group = "run paper"
    minecraftVersion(newestTestedVersion)
    pluginJars(tasks.jar.flatMap { it.archiveFile })
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    runDirectory = rootProject.layout.projectDirectory.dir("run/$newestTestedVersion")
    jvmArgs("-Dcom.mojang.eula.agree=true")
}
