import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

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

val fatJar by tasks.registering(Jar::class) {
    group = "distribution"
    description = "Build a self-contained C+ CLI executable JAR."
    archiveBaseName.set("cplus-cli")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    dependsOn(tasks.named("classes"))
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { file ->
        if (file.isDirectory) file else zipTree(file)
    })
}

tasks.named("assemble") {
    dependsOn(fatJar)
}
