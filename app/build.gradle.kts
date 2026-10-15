plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Satu-satunya versi game yang didukung app ini.
val mcVersion = "1.21.11"

android {
    namespace = "com.example.cubechat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cubechat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mc$mcVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/io.netty.versions.properties",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/{AL2.0,LGPL2.1}"
            )
        }
    }
}

configurations.all {
    exclude(group = "com.intellij", module = "annotations")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Protokol Minecraft Java: rilis resmi MCProtocolLib untuk 1.21.11 (versi "1.21.11-1", bytecode Java 17).
    // Kalau tidak ada di repo.opencollab.dev, workflow CI membangunnya dari source (lihat build.yml).
    implementation("org.geysermc.mcprotocollib:protocol:$mcVersion-1")
}
