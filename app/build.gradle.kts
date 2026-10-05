import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

val ksFile = listOfNotNull(System.getenv("AMNI_PRICE_SIGNING"), rootProject.file("keystore.properties").path, "${System.getProperty("user.home")}/.config/amni/signing/android/amni-price.properties").map(::File).firstOrNull { it.exists() }
val ksProp = Properties().apply { ksFile?.inputStream()?.use { load(it) } }
android {
    namespace = "com.amniscient.price"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amniscient.price"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1"

        // Map tiles: override in gradle.properties / local.properties with your provider before publishing.
        val tileUrl = (project.findProperty("amni.mapTileUrl") as String?) ?: "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        val attribution = (project.findProperty("amni.mapAttribution") as String?) ?: "© OpenStreetMap contributors"
        buildConfigField("String", "MAP_TILE_URL", "\"$tileUrl\"")
        buildConfigField("String", "MAP_ATTRIBUTION", "\"$attribution\"")
    }

    signingConfigs {
        create("release") {
            storeFile = ksProp.getProperty("storeFile")?.let { file(it) }
            storePassword = ksProp.getProperty("storePassword", "")
            keyAlias = ksProp.getProperty("keyAlias", "")
            keyPassword = ksProp.getProperty("keyPassword", "")
        }
    }
    buildTypes {
        release {
            signingConfig = if (ksFile != null) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        // Lint's Kotlin analyzer crashes on some test files (a lint bug); app sources are still fully checked.
        ignoreTestSources = true
    }
    sourceSets {
        // Exported Room schemas, so migration tests can build every historical version.
        // Robolectric reads the debug variant's assets; release builds never include these.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // On-device ML Kit models (bundled: works offline, no Play Services download).
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.kotlinx.coroutines.play.services)

    // Price map (OpenStreetMap-based, no API key or Play Services needed).
    implementation(libs.osmdroid)

    testImplementation(libs.junit)
    // Screenshot tests: real screens rendered on the JVM (no emulator needed).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
