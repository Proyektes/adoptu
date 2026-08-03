plugins {
    kotlin("multiplatform") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
}

group = "com.adoptu"
version = "1.0.0"

repositories {
    mavenCentral()
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
