import java.io.File
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    signingConfigs {
        create("release") {
            val envStoreFile = System.getenv("STORETD_KEYSTORE_FILE").orEmpty()
            val fallbackStoreFile = File(rootProject.projectDir, "../storetd-release.jks").absolutePath
            val selectedStoreFile = if (envStoreFile.isNotBlank()) envStoreFile else fallbackStoreFile

            storeFile = File(selectedStoreFile)
            storePassword = System.getenv("STORETD_KEYSTORE_PASSWORD").orEmpty()
            keyAlias = System.getenv("STORETD_KEY_ALIAS").orEmpty()
            keyPassword = System.getenv("STORETD_KEY_PASSWORD").orEmpty()
        }
    }

    namespace = "com.storetd.play"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.storetd.play"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${System.getenv("API_BASE_URL") ?: "https://storetd-play-backend.onrender.com"}\""
        )
        buildConfigField(
            "String",
            "SUPPORT_WHATSAPP",
            "\"${System.getenv("SUPPORT_WHATSAPP") ?: "5490000000000"}\""
        )
        buildConfigField(
            "String",
            "SUPPORT_EMAIL",
            "\"${System.getenv("SUPPORT_EMAIL") ?: "soporte@example.com"}\""
        )
        resValue("string", "app_name", System.getenv("APP_NAME") ?: "StoreTD Play")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
