plugins {
    // Lets Gradle download JDK 25 for the 26.x test server when it is not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "betahub-minecraft-plugin"

include("core", "paper")
