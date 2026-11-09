import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from <root>/keystore.properties (never committed). Without it the
// release build falls back to the debug key so `assembleRelease` still produces an installable APK.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.mvoon"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.mvoon"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "LIBVLC_VERSION", "\"${libs.versions.libvlc.get()}\"")
        buildConfigField("String", "FFMPEG_DECODER_VERSION", "\"${libs.versions.ffmpegDecoder.get()}\"")
        // GPL-3.0: the app must point to its complete source code. Set these in gradle.properties.
        buildConfigField("String", "SOURCE_URL", "\"${providers.gradleProperty("mvoon.sourceUrl").get()}\"")
        buildConfigField("String", "PRIVACY_URL", "\"${providers.gradleProperty("mvoon.privacyUrl").get()}\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // One APK per ABI keeps the download small (libVLC alone is ~46 MB per ABI); the universal APK
    // runs on any phone.
    // Not for the Play bundle: Play makes per-device APKs from the .aab itself, and AGP can't shrink
    // resources for a bundle and ABI splits in one build (issuetracker.google.com/402800800).
    val buildingBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    splits {
        abi {
            isEnable = !buildingBundle
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version")
        // libVLC and the FFmpeg extension both ship the C++ runtime.
        jniLibs.pickFirsts += "**/libc++_shared.so"
        // No phones use 32-bit x86; dropping it saves ~45 MB in the universal APK.
        jniLibs.excludes += "lib/x86/**"
        // Keep native libs uncompressed & page-aligned: faster install/start, smaller on-device footprint.
        jniLibs.useLegacyPackaging = false
    }

    lint {
        disable += "UnsafeOptInUsageError"
    }

    testOptions {
        animationsDisabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn.add("androidx.compose.ui.text.ExperimentalTextApi")
        optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":subtitles"))
    implementation(project(":engine"))
    implementation(project(":engine-vlc"))
    implementation(project(":library"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.lucide.icons)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
