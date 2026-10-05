plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.studiokinematics.nexa"
    compileSdk = 36

    defaultConfig {
        val youtubeKey = providers.gradleProperty("YOUTUBE_API_KEY").orNull ?: ""
        val nexaPlusProductId = providers.gradleProperty("NEXA_PLUS_PRODUCT_ID").orNull ?: ""
        buildConfigField("String", "YOUTUBE_API_KEY", "\"${youtubeKey.replace("\"", "\\\"")}\"")
        buildConfigField("String", "NEXA_PLUS_PRODUCT_ID", "\"${nexaPlusProductId.replace("\"", "\\\"")}\"")
        applicationId = "com.studiokinematics.nexa"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.9.3")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.3")
    implementation("androidx.media3:media3-common:1.9.3")
    implementation("androidx.media3:media3-session:1.9.3")
    implementation("androidx.media3:media3-datasource:1.9.3")
    implementation("androidx.media3:media3-database:1.9.3")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    testImplementation("junit:junit:4.13.2")
}
