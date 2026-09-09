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
        maven { url = uri("https://jitpack.io") } // for termux terminal-view/terminal-emulator
    }
}

rootProject.name = "umlinux"
include(":app")
