plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("cplus.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
}

dependencies {
    implementation(project(":compiler"))
    implementation(project(":language-core"))
    implementation(project(":semantic"))
    implementation(project(":c-backend"))
}
