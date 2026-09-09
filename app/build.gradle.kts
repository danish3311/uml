plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.umlinux.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.umlinux.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        // The UML kernel binary we download is arm64 only.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }

    // The bundled kernel/stub/rootfs binaries under assets/uml/ are already
    // dense machine code - don't waste time deflating them into the APK.
    androidResources {
        noCompress += listOf("bin")
    }
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://github.com/termux/termux-app/raw/apt-repo/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1") // ActivityResultContracts.OpenDocument for the rootfs file picker
    
    // Termux libraries
    implementation("com.termux:termux-app:0.118.0")
    implementation("com.termux:termux-shared:0.118.0")
}
