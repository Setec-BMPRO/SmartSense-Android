plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.smartsense.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smartsense.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
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

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            output.outputFileName = "app-smartsense-${variant.name}-v${variant.versionName}.apk"
        }
    }
}

dependencies {
    // --- AndroidX & Core ---
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.fragment.ktx)

    // --- Navigation ---
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    // --- Lifecycle ---
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)

    // --- Dependency Injection (Hilt) ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // --- WorkManager ---
    implementation(libs.work.runtime.ktx)

    // --- Data Storage (Room & DataStore) ---
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.sqlite.ktx)
    implementation(libs.sqlite)

    // --- Coroutines ---
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    // --- Firebase ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    // --- Bluetooth & Utility ---
    implementation(libs.nordic.scanner)
    implementation(libs.play.services.location)
    implementation(libs.timber)

    // --- UI Helpers (Groupie) ---
    implementation(libs.groupie)
    implementation(libs.groupie.viewbinding)

    // --- Testing ---
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}