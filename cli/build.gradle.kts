plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("cplus.cli.MainKt")
}

dependencies {
    implementation(project(":compiler"))
    implementation(project(":language-core"))
    implementation(project(":c-backend"))
}
