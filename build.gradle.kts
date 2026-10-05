plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
}
extra["tesseract4AndroidVersion"] = "4.9.0"
subprojects {
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
            val maps = listOf("-ffile-prefix-map=${rootDir.absolutePath}/=", "-ffile-prefix-map=${sdkDirectory.absolutePath}/=sdk/")
            defaultConfig.externalNativeBuild.cmake { cFlags += maps; cppFlags += maps; arguments += "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--build-id=none" }
        }
    }
}
