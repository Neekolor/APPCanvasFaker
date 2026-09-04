@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://api.xposed.info/")
        maven(url = "https://repo.libxposed.org/maven")
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "ACF0820"
include(":app")