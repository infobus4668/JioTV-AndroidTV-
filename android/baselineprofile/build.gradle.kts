plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.fenyx.jtv.baselineprofile"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // The benchmark runs on a generator device/emulator, not on the shipped app. Its minSdk only
        // gates where the benchmark can run (Macrobenchmark floor); the app's own minSdk stays 24 and
        // the generated profile still benefits API 24+ devices at runtime.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The app whose journeys we exercise to produce the profile.
    targetProjectPath = ":app"
}

kotlin {
    jvmToolchain(17)
}

// Generate against a connected device/emulator (API 33+, or a rooted/AOSP image). Run:
//   ./gradlew :app:generateReleaseBaselineProfile
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
