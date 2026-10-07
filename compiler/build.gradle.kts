plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":language-core"))
    implementation(project(":semantic"))
    implementation(project(":comptime"))
    implementation(project(":c-backend"))
}
