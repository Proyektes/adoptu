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
plugins {
    // As an included (composite) build, this doesn't inherit the root project's
    // settings.gradle.kts -- without its own copy of this plugin, `scan`'s jvmToolchain(21)
    // has no toolchain download repository configured and fails to auto-provision JDK 21 on
    // hosts/containers (e.g. the adoptu Docker builder image) that only have GraalVM 25.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "dfk-codegen"
include("scan")
