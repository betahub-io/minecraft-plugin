plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    // Gson ships with Paper, so it is not bundled into the plugin jar.
    compileOnly(libs.gson)

    testImplementation(libs.gson)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}
