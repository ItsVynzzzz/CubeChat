pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Terisi hanya kalau CI membangun MCProtocolLib dari source (fallback).
        mavenLocal {
            content { includeGroup("org.geysermc.mcprotocollib") }
        }
        google()
        mavenCentral()
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.opencollab.dev/maven-releases/")
        maven("https://jitpack.io")
    }
}
rootProject.name = "CubeChat"
include(":app")
