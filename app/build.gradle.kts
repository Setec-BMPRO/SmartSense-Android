plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    kotlin("kapt")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.smartsense.app"
    compileSdk = 35
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.smartsense.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation(libs.material)
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)

    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.fragment.ktx)

    implementation(libs.hilt.android)

    kapt(libs.hilt.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.jakewharton.timber:timber:5.0.1")

    implementation("com.github.lisawray.groupie:groupie:2.10.1")
    implementation("com.github.lisawray.groupie:groupie-viewbinding:2.10.1")

    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    // Hilt WorkManager Integration
    implementation("androidx.hilt:hilt-work:1.2.0")

    // Use Kapt or KSP for the compiler (match what you use for Hilt)
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // Standard WorkManager (Kotlin + Coroutines support)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("no.nordicsemi.android.support.v18:scanner:1.6.0")

}
