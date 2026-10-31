plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

// The screens and their parts, shared by the foss and pro apps: one tuner,
// two editions (4 Oct 2026). The apps are shells that mount these; Pro adds
// the book beside them. Nothing of the book lives here.
//
// Since 31 Oct 2026 the screens are Compose Multiplatform, so the same code
// draws on Android and on the iPhone (a port, not a copy). What only one
// platform can do -- the crash note, the preferences, the fonts and the
// photograph as Android resources -- lives in androidMain and iosMain behind
// a few expect/actual declarations; everything else is commonMain.
kotlin {
    androidLibrary {
        namespace = "at.clavierhaus.unisonmaster.ui"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
        // the layout check renders the screens on the JVM (LayoutCheckTest): Robolectric draws them natively
        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = true
        }
    }

    // no iosX64: Compose Multiplatform 1.12 no longer builds for the Intel simulator. The iPhone app
    // (iosApp/) links the screens as one static framework, the engine inside it.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ClavierTuner"
            isStatic = true
            export(project(":shared"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui)
            implementation(libs.androidx.compose.material3)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("robolectric.graphicsMode", "NATIVE")
    maxHeapSize = "2g"
}
