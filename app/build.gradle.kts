plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin)
    alias(libs.plugins.maps.secrets)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
}

android {
    namespace = "com.sdevprem.runtrack"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sdevprem.runtrack"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Add Room schema location
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Enable R8 optimization
            isDebuggable = false
        }
        debug {
            isDebuggable = true
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
        compose = true
        buildConfig = true
        viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/demo_profile_pic.png"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Maps
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Hilt
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    kapt(libs.androidx.room.compiler)

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.9.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.2")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    //kotlin
    implementation(platform(libs.kotlin.bom))

    //datastore
    implementation(libs.androidx.datastore.preferences)

    //coil
    implementation(libs.coil.compose)

    //paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    //permission
    implementation(libs.accompanist.permissions)

    //vico
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)

    // Add performance monitoring
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.4")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.5.4")
    
    // Add crash reporting
    implementation("com.google.firebase:firebase-crashlytics:18.4.3")
    implementation("com.google.firebase:firebase-analytics:21.5.0")
    
    // Timber for logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("org.mockito:mockito-core:4.5.1")
    testImplementation("org.mockito:mockito-junit-jupiter:4.5.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.0")
}

kapt {
    correctErrorTypes = true
}