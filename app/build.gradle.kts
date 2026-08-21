plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.serialization")
    // alias(libs.plugins.google.services) // Commented out to fix missing google-services.json error
}

android {
    namespace = "com.example.recetapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.recetapp.v2"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
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
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // Supabase
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.0.1")
    implementation("io.github.jan-tennert.supabase:auth-kt:3.0.1")
    implementation("io.github.jan-tennert.supabase:storage-kt:3.0.1")
    implementation("io.github.jan-tennert.supabase:realtime-kt:3.0.1")
    implementation("io.github.jan-tennert.supabase:supabase-kt:3.0.1")
    implementation("io.ktor:ktor-client-android:3.0.1")
    implementation("io.ktor:ktor-client-okhttp:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.coil-kt:coil:2.6.0")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.inappmessaging)
    implementation(libs.firebase.analytics)

    // Google Auth
    implementation(libs.credman)
    implementation(libs.credman.play)
    implementation(libs.google.signin)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}