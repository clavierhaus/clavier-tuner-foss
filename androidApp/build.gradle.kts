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

    // Pinned: F-Droid's reproducible-build guidance asks for the exact build-tools, because a
    // change of build-tools changes the XML and PNG output (f-droid.org/docs/Reproducible_Builds,
    // read 10 Oct 2026). 36.0.0 is what the X1 has installed; the signing step uses apksigner
    // from build-tools 34 separately (scripts/release.sh), as the same guidance asks.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "at.clavierhaus.claviertuner"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        // A plain version for the release: F-Droid's recipe names the versionName and it must
        // equal the APK's exactly (Build Metadata Reference, Builds.versionName). The commit the
        // APK was built from goes after it in DEBUG builds only (below): Settings shows it, so
        // "is the phone running the latest build?" is answered by a glance and not by reasoning
        // (8 Oct 2026: a free APK on the Pixel did not do what the checkout's code does, and
        // nothing on the phone could say which code it was).
        versionName = "0.4.0"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No META-INF/version-control-info.textproto: since AGP 8.3 the release APK carries the
            // git revision it was built from, and that file was the ONE difference between our
            // reference APK of v0.1.0 and F-Droid's rebuild of the same commit (fork CI job
            // 17081469811, 10 Oct 2026): ours said revision c54eff1, theirs said
            // NO_VALID_GIT_FOUND, because their build does not see a git tree. Two builds of one
            // commit here agree on it, so the proof (scripts/verify-reproducible.sh) cannot catch
            // it; the tag names the commit anyway.
            vcsInfo { include = false }
        }
        debug {
            versionNameSuffix = "+$gitShortHash"
        }
    }

    // No dependency metadata block in the APK: it is a Google-signed blob F-Droid cannot
    // reproduce, and F-Droid asks apps it verifies against the developer's APK to leave it out
    // (fdroiddata issue 3330).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// No ART baseline profile in the APK: the Compose libraries ship profiles, the merged
// baseline.profm is not deterministic, and F-Droid's guidance disables the task for a build
// that must reproduce (f-droid.org/docs/Reproducible_Builds, "baseline.prof").
tasks.whenTaskAdded {
    if (name.contains("ArtProfile")) enabled = false
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
