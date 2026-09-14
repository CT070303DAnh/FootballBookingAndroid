// build.gradle.kts (Module :app)
// Kotlin DSL version — tương thích với project đang có
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")  // Firebase plugin
}

android {
    namespace = "com.example.footballbooking"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.footballbooking"
        minSdk = 24         // Android 7.0+
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Đọc API Key từ local.properties (an toàn, không commit lên Git)
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localProps.load(FileInputStream(localPropsFile))
        }

        val openWeatherKey: String = localProps.getProperty("OPENWEATHER_API_KEY")
            ?: (project.findProperty("OPENWEATHER_API_KEY") as String? ?: "")
        val vnpaySecret: String = localProps.getProperty("VNPAY_HASH_SECRET")
            ?: (project.findProperty("VNPAY_HASH_SECRET") as String? ?: "")
        val mapsApiKey: String = localProps.getProperty("GOOGLE_MAPS_API_KEY")
            ?: (project.findProperty("GOOGLE_MAPS_API_KEY") as String? ?: "")
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"$openWeatherKey\"")
        buildConfigField("String", "VNPAY_HASH_SECRET", "\"$vnpaySecret\"")
        // Inject Maps API Key vào AndroidManifest
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // BẬT ViewBinding — bắt buộc
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // ==================== ANDROID CORE ====================
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.activity.ktx)

    // ==================== LIFECYCLE (ViewModel + LiveData) ====================
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.3")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.3")

    // Navigation Component
    implementation("androidx.navigation:navigation-fragment:2.8.0")
    implementation("androidx.navigation:navigation-ui:2.8.0")

    // ==================== FIREBASE BOM ====================
    // BOM tự quản lý version tương thích cho tất cả Firebase SDK
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-analytics")

    // ==================== GOOGLE MAPS & LOCATION ====================
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ==================== NETWORKING (Retrofit + OkHttp) ====================
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ==================== IMAGE LOADING ====================
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // ==================== CHART (Admin thống kê) ====================
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ==================== UI UTILITIES ====================
    implementation("de.hdodenhof:circleimageview:3.1.0")      // Avatar tròn
    implementation("com.facebook.shimmer:shimmer:0.5.0")       // Skeleton loading
    implementation("com.airbnb.android:lottie:6.4.0")          // Lottie animation
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // ==================== TESTING ====================
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}