plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.example.sagip_prototype"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.sagip_prototype"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 16 KB page size support
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    
    // Native library configuration for 16 KB page size support
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        // Resolve AAR metadata issues
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation("com.google.firebase:firebase-appcheck:17.1.2")
    implementation("com.google.firebase:firebase-appcheck-playintegrity:17.1.2")
    implementation (libs.picasso)
    implementation(libs.material.v190)
    
    // Image cropping - using built-in Android functionality
    // No external dependencies needed
    
    
    // WorkManager for reliable background tasks (FCM alternative)
    implementation("androidx.work:work-runtime:2.9.0")
    
    // Google Maps Navigation SDK (with Java 8 compatibility)
    implementation("com.google.android.libraries.navigation:navigation:6.2.0") {
        exclude(group = "com.google.android.gms", module = "play-services-maps")
    }
    
    // Location services
    implementation("com.google.android.gms:play-services-location:21.0.1")
    
    // CameraX dependencies - Updated for better compatibility
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // ML Kit Face Detection - Updated for 16 KB compatibility
    implementation("com.google.mlkit:face-detection:16.1.5")
    
    // Google Guava for ListenableFuture
    implementation("com.google.guava:guava:32.1.3-android")
    
    // Core library desugaring for Java 8+ APIs
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.swiperefreshlayout)


    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

}
