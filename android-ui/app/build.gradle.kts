plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // org.jetbrains.kotlin.android is removed — AGP 9 built-in Kotlin handles it
}

android {
    namespace = "com.maxixa.lemon"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.maxixa.lemon"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // AGP 9 built-in Kotlin: compiler options are configured inside android {}
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.5.0-alpha08")
}
