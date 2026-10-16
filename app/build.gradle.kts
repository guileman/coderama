plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// URL donde GitHub Pages publica los precios procesados.
// En GitHub Actions se arma sola a partir del usuario y el nombre del repositorio.
val dataBaseUrl: String = System.getenv("CODERAMA_DATA_URL")
    ?: (project.findProperty("coderamaDataUrl") as String?)
    ?: "https://USUARIO.github.io/coderama/"

val ciVersionCode: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "ar.coderama.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.coderama.app"
        minSdk = 24
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = "0.1.$ciVersionCode"
        buildConfigField("String", "DATA_BASE_URL", "\"$dataBaseUrl\"")
        // Solo procesadores de teléfonos (sin emuladores x86): el APK pesa la mitad
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // Clave fija incluida en el repo para que cada APK nuevo se instale encima del anterior.
        // Sirve para uso personal; para publicar en Play Store hay que usar una clave privada.
        create("coderama") {
            storeFile = file("keystore/coderama-debug.jks")
            storePassword = "coderama"
            keyAlias = "coderama"
            keyPassword = "coderama"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("coderama")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("coderama")
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
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Cámara y lectura de códigos / texto, todo dentro del teléfono
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
