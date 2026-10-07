pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "cplus"

include(
    ":language-core",
    ":semantic",
    ":comptime",
    ":c-backend",
    ":compiler",
    ":cli"
)
