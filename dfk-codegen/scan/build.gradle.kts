plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

// Env var first (terminal builds), falling back to a Gradle property of the same name --
// same helper backend/build.gradle.kts uses for its own GitHub-Packages-consumed Universaliun
// libraries.
fun credential(name: String): String? = System.getenv(name) ?: findProperty(name) as String?

repositories {
    mavenCentral()

    // DataFormatsKit (library + KSP processor) -- see Libraries/DataFormatsKit/README.md.
    // GITHUB_ACTOR / DATA_FORMATS_KIT_TOKEN in the environment; content{} scopes this repository
    // to the bare com.universaliun group (DataFormatsKit's own coordinates).
    maven {
        name = "DataFormatsKitGitHubPackages"
        url = uri("https://maven.pkg.github.com/ULibraries/DataFormatsKit")
        credentials {
            username = credential("GITHUB_ACTOR")
            password = credential("DATA_FORMATS_KIT_TOKEN")
        }
        content { includeGroup("com.universaliun") }
    }
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
    implementation("com.universaliun:dataformatskit-jvm:0.1.0")
    // The KSP processor itself.
    ksp("com.universaliun:dataformatskit-codegen-processor:0.1.0")
}
