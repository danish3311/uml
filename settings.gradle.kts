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
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://raw.githubusercontent.com/termux/termux-app/gh-pages/maven-repo") } // for termux terminal-view/terminal-emulator
    }
}

rootProject.name = "umlinux"
include(":app")
