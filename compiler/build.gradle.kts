plugins {
    kotlin("jvm")
}

tasks.test {
    inputs.files(rootProject.fileTree("sdk") {
        exclude("cache/**", "**/build/**")
    }).withPropertyName("sdkTestSources")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":language-core"))
    implementation(project(":semantic"))
    implementation(project(":comptime"))
    implementation(project(":c-backend"))
}
