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
        google()
        mavenCentral()
    }
}

rootProject.name = "Mvoon"

// Platform-neutral logic (pure Kotlin — ready to become Kotlin Multiplatform `commonMain`).
include(":core")
include(":subtitles")
// Android implementations.
include(":engine")
include(":library")
// Optional compatibility engine (libVLC) for files the phone cannot decode.
include(":engine-vlc")
// Android UI.
include(":app")
