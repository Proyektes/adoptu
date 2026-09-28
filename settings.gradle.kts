plugins {
    // Lets the GraalVM native-image toolchain (JavaLanguageVersion + GraalVM vendor,
    // configured in backend/build.gradle.kts) be auto-provisioned instead of requiring
    // a manually installed GraalVM SDK on the build machine.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "adopt-u"
include("backend")
include("frontend")
include("common")

// DataFormatsKit (JSON codecs replacing Jackson for backend/{dto,web}) and its Helidon 4 media
// support -- see backend/src/main/kotlin/com/adoptu/web/JsonSupport.kt and
// docs/codegen-consumers.md / docs/helidon.md in the library repo.
// TODO replace by the GitHub Packages artifact once DataFormatsKit 0.1.0 is published
includeBuild("/home/laf/Proyektes/Libraries/DataFormatsKit/.worktrees/adoption-integration") {
    dependencySubstitution {
        substitute(module("com.universaliun:dataformatskit-helidon-media")).using(project(":helidon-media"))
        substitute(module("com.universaliun:DataFormatsKit-jvm")).using(project(":"))
    }
}

// KSP codegen scan build for backend/src/main/kotlin/com/adoptu/dto/** -- isolated Kotlin
// 2.3.10 + KSP composite build (this project's root is Kotlin 2.4.0, and KSP has no 2.4.x
// release yet). See dfk-codegen/settings.gradle.kts and docs/codegen-consumers.md section 3
// ("Applying the processor to a Kotlin 2.4 Gradle project") in the DataFormatsKit repo.
includeBuild("dfk-codegen")
