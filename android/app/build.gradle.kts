plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hamfilm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hamfilm.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.2"
        // آدرس پیش‌فرض بک‌اند — کاربر می‌تواند از تنظیمات، بین کلادفلر و VPS جابه‌جا کند
        buildConfigField("String", "DEFAULT_API_BASE", "\"https://hamfilm-worker.ai-showcase-shir.workers.dev/\"")
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "DEBUG_LOG", "false")
        }
        debug {
            buildConfigField("boolean", "DEBUG_LOG", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // شبکه
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // پخش ویدیو (ExoPlayer / Media3)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // تصاویر
    implementation("io.coil-kt:coil-compose:2.7.0")

    // کوروتین
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ذخیره‌سازی امن توکن
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
