plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.todoagent"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.todoagent"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            merges += "**/META-INF/INDEX.LIST"
            merges += "**/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {

    implementation("com.google.adk:google-adk-kotlin-core:1.1.0")
    ksp("com.google.adk:google-adk-kotlin-processor:1.1.0")
    implementation("com.google.adk:google-adk-kotlin-firebase-android:1.1.0")
    // On-device Gemini Nano via ML Kit (beta in 1.0). Uncomment to try the offline agent.
    // implementation("com.google.adk:google-adk-kotlin-mlkit-android:1.0.0-beta")
    // VERIFY: RoomSessionService may live in its own artifact. Check the "Modules" table
    // in github.com/google/adk-kotlin and add it here if the import doesn't resolve.

    // --- Firebase AI Logic ---
    // Bump the BoM to the latest version Android Studio suggests.
    implementation(platform("com.google.firebase:firebase-bom:33.14.0"))
    implementation("com.google.firebase:firebase-ai")

    // --- Room (our own task database) ---
    val room = "2.7.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    implementation("com.google.firebase:firebase-appcheck-debug")
}