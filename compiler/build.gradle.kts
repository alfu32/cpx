plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":language-core"))
    implementation(project(":semantic"))
    implementation(project(":c-backend"))
}
