pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version "2.3.10"
        id("com.google.devtools.ksp") version "2.3.10"
    }
}
rootProject.name = "dfk-codegen"
include("scan")
