pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AmniPrice"
include(":app")
include(":tesseract4android")
project(":tesseract4android").projectDir = file("external/Tesseract4Android/tesseract4android")
