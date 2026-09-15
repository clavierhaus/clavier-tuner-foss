pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "clavierhaus-unisonmaster"

// Core modules come from one list, shared with clavier-tuner-pro.
apply(from = "core-modules.settings.gradle.kts")
include(":androidApp")
