plugins {
    // Lets the GraalVM native-image toolchain (JavaLanguageVersion + GraalVM vendor,
    // configured in backend/build.gradle.kts) be auto-provisioned instead of requiring
    // a manually installed GraalVM SDK on the build machine.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "adopt-u"
include("backend")
include("frontend")
