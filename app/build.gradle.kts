import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val defaultApiBaseUrl = "https://alimentaria.simpler.bo/api/v1/"
val apiBaseUrl: String = (project.findProperty("wtracker.apiBaseUrl") as String?) ?: defaultApiBaseUrl

val defaultWooCommerceBaseUrl = "https://mobarmy.dev/alimentaria/"
val wooCommerceBaseUrl: String = (project.findProperty("woocommerce.baseUrl") as String?) ?: defaultWooCommerceBaseUrl
val defaultWooCommerceConsumerKey = "ck_92642c263cc189744202ba5796fa67a165da99bb"
val wooCommerceConsumerKey: String = (project.findProperty("woocommerce.consumerKey") as String?) ?: defaultWooCommerceConsumerKey
val defaultWooCommerceConsumerSecret = "cs_f13ee32a9ff55bea870b5c0ac59a3ee01f8e19ad"
val wooCommerceConsumerSecret: String = (project.findProperty("woocommerce.consumerSecret") as String?) ?: defaultWooCommerceConsumerSecret

// Credenciales de firma: viven fuera del repo (keystore.properties está en .gitignore).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.wtracker.field"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wtracker.field"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.1.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "WOOCOMMERCE_BASE_URL", "\"$wooCommerceBaseUrl\"")
        buildConfigField("String", "WOOCOMMERCE_CONSUMER_KEY", "\"$wooCommerceConsumerKey\"")
        buildConfigField("String", "WOOCOMMERCE_CONSUMER_SECRET", "\"$wooCommerceConsumerSecret\"")
    }

    signingConfigs {
        // La misma clave firma todas las versiones publicadas: Android sólo actualiza APKs con la firma original.
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core / UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Persistencia local (offline-first)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Sincronización en segundo plano
    implementation(libs.androidx.work.runtime.ktx)

    // Red
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Cámara in-app (sin galería)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)

    // Ubicación y mapa
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.osmdroid.android)

    // Gamification
    implementation("nl.dionsegijn:konfetti-compose:2.0.4")
}
