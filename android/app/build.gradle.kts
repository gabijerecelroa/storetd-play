import java.io.File
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val envProps = Properties()
val envFile = rootProject.file("../.env")
if (envFile.exists()) {
    envProps.load(FileInputStream(envFile))
} else {
    val localEnv = rootProject.file(".env")
    if (localEnv.exists()) {
        envProps.load(FileInputStream(localEnv))
    }
}

fun getEnv(key: String, default: String = ""): String {
    return System.getenv(key) ?: envProps.getProperty(key) ?: default
}

android {
    signingConfigs {
        create("release") {
            val envStoreFile = getEnv("STORETD_KEYSTORE_FILE", "")
            val fallbackStoreFile = File(rootProject.projectDir, "../storetd-release.jks").absolutePath
            val selectedStoreFile = if (envStoreFile.isNotBlank()) envStoreFile else fallbackStoreFile

            storeFile = File(selectedStoreFile)
            storePassword = getEnv("STORETD_KEYSTORE_PASSWORD", "")
            keyAlias = getEnv("STORETD_KEY_ALIAS", "")
            keyPassword = getEnv("STORETD_KEY_PASSWORD", "")
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
        versionCode = 118
        versionName = "1.6.83"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${getEnv("API_BASE_URL", "http://tv.m3uts.xyz")}\""
        )
        buildConfigField(
            "String",
            "XTREAM_USER",
            "\"${getEnv("XTREAM_USER", "m")}\""
        )
        buildConfigField(
            "String",
            "XTREAM_PASSWORD",
            "\"${getEnv("XTREAM_PASSWORD", "m")}\""
        )
        buildConfigField(
            "String",
            "SUPPORT_WHATSAPP",
            "\"${getEnv("SUPPORT_WHATSAPP", "5493718698291")}\""
        )
        buildConfigField(
            "String",
            "SUPPORT_EMAIL",
            "\"${getEnv("SUPPORT_EMAIL", "")}\""
        )
        resValue("string", "app_name", getEnv("APP_NAME", "StoreTD Play"))
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
