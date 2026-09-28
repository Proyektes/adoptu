plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

// TODO replace by the GitHub Packages artifact once DataFormatsKit 0.1.0 is published --
// mavenLocal() first because the orchestrating session publishes the library + processor there
// before this build runs (see docs/codegen-consumers.md section 4, "Publishing", in the
// DataFormatsKit repo: consumers normally have no mavenLocal() at all, but the artifact isn't on
// GitHub Packages yet).
repositories {
    mavenLocal()
    mavenCentral()
}

kotlin { jvmToolchain(21) }

// Distinguishes this module's generated file facade class from any other consumer's scan on the
// same classpath (they all share the com.universaliun.formats.json.generated package) -- see
// docs/codegen-consumers.md section 3 in the DataFormatsKit repo and JsonDecodableProcessor's
// `generatedFileName` parameter.
ksp {
    arg("dfk.generatedFileName", "GeneratedCodecs_adoptu")
}

// Compile ONLY the annotated DTO files (plus whatever plain types they reference) in an isolated
// 2.3.10 compilation -- enough for KSP to read their constructor shapes. adopt-u's DTOs pull in
// no framework imports (verified: only kotlin.* and each other), so this isolated compile stays
// self-contained.
kotlin.sourceSets.named("main") {
    kotlin.srcDir("../../backend/src/main/kotlin")
    kotlin.include(
        "com/adoptu/dto/**",
        "com/adoptu/web/JsonResponses.kt",
    )
}

dependencies {
    // The annotations the DTOs carry, and the JsonConverter SPI.
    implementation("com.universaliun:DataFormatsKit-jvm:0.1.0")
    // The KSP processor itself.
    ksp("com.universaliun:dataformatskit-codegen-processor:0.1.0")
}
