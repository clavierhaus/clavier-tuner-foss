plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

/** The short hash of the checkout this build comes from; "nogit" when it cannot be read. */
val gitShortHash: String = runCatching {
    ProcessBuilder("git", "rev-parse", "--short", "HEAD").directory(rootDir).redirectErrorStream(true)
        .start().inputStream.bufferedReader().readText().trim().takeIf { it.matches(Regex("[0-9a-f]{7,}")) }
}.getOrNull() ?: "nogit"

android {
    namespace = "at.clavierhaus.unisonmaster.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "at.clavierhaus.claviertuner"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        // The commit the APK was built from, after the version: Settings shows it, so "is the
        // phone running the latest build?" is answered by a glance and not by reasoning
        // (8 Oct 2026: a free APK on the Pixel did not do what the checkout's code does, and
        // nothing on the phone could say which code it was).
        versionName = "0.1.0+$gitShortHash"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
}
