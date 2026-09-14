plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    // AGP 9 KMP integration: the android target is configured here,
    // via the com.android.kotlin.multiplatform.library plugin.
    androidLibrary {
        namespace = "at.clavierhaus.unisonmaster.shared"
        compileSdk = 37
        minSdk = 26

        withHostTestBuilder {}
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
