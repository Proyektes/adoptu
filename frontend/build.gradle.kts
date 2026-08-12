plugins {
    kotlin("multiplatform") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
}

group = "com.adoptu"
version = "1.0.0"

// Env var first (terminal builds), falling back to a Gradle property of the same name -- matches
// backend/build.gradle.kts's own credential(...) helper for the AuthKitGitHubPackages repo below.
fun Project.credential(name: String): String? = System.getenv(name) ?: findProperty(name) as String?

repositories {
    mavenCentral()
    // AuthKit's web module (WebAuthn browser bridge) -- see backend/build.gradle.kts's identical
    // AuthKitGitHubPackages block for the credentials this needs (GITHUB_ACTOR / AUTH_KIT_TOKEN).
    maven {
        name = "AuthKitGitHubPackages"
        url = uri("https://maven.pkg.github.com/ULibraries/AuthKit")
        credentials {
            username = credential("GITHUB_ACTOR")
            password = credential("AUTH_KIT_TOKEN")
        }
        content { includeGroup("com.universaliun.auth") }
    }
}

kotlin {
    js(IR) {
        browser {
            binaries.executable()
            commonWebpackConfig {
                outputFileName = "common.js"
            }
        }
        compilerOptions {
            freeCompilerArgs.addAll(listOf("-opt-in=kotlin.js.ExperimentalJsExport"))
        }
    }
    // JVM target used only at build time by SiteGenerator.kt (generateSite task below) - renders
    // the site.pages/*.kt kotlinx.html templates (moved here from backend/pages/*.kt) to static
    // .html files. Never shipped to the browser; the js(IR) target above is what actually runs
    // client-side.
    jvm()
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                // AuthKit's WebAuthn browser bridge (createPasskeyCredential/getPasskeyAssertion)
                // -- replaces this module's own WebAuthn.kt, which hand-rolled the same
                // navigator.credentials/base64url logic with a wrong assumption about the
                // backend's optionsJson wire shape (flat, when it's actually wrapped in
                // {"publicKey": ...} -- see WebAuthn.kt's replacement for detail). Needs
                // kotlinx-coroutines-core for its suspend functions and for bridging back to the
                // Promise-based call sites here via kotlinx.coroutines.promise.
                implementation("com.universaliun.auth:authkit-web:1.2.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project(":common"))
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0")
            }
        }
    }
}

// SCSS -> CSS: moved here from backend/build.gradle.kts (same standalone Dart Sass CLI approach,
// no npm/Node) now that the site (markup + styling) lives in this module, not :backend.
val scssSrcDir = layout.projectDirectory.dir("src/main/scss")
val cssOutDir = layout.buildDirectory.dir("generated/scss/main/static/css")

val compileSass by tasks.registering(Exec::class) {
    inputs.dir(scssSrcDir)
    outputs.dir(cssOutDir)
    doFirst { cssOutDir.get().asFile.mkdirs() }
    commandLine(
        "sass",
        "--no-source-map",
        "--style=expanded",
        "${scssSrcDir.asFile}:${cssOutDir.get().asFile}",
    )
}

val siteOutDir = layout.buildDirectory.dir("site")

val generateSite by tasks.registering(JavaExec::class) {
    dependsOn(compileSass, tasks.named("jsBrowserProductionWebpack"))
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    dependsOn(jvmMain.compileTaskProvider)
    classpath = jvmMain.output.allOutputs + jvmMain.runtimeDependencyFiles!!
    mainClass.set("com.adoptu.site.SiteGeneratorKt")
    args(
        cssOutDir.get().asFile.absolutePath,
        layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable").get().asFile.absolutePath,
        siteOutDir.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("serveSite") {
    dependsOn(generateSite)
    workingDir = rootDir
    commandLine("python3", "scripts/serve_site.py", siteOutDir.get().asFile.absolutePath)
}
