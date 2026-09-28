import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import kotlinx.kover.gradle.plugin.dsl.GroupingEntityType

plugins {
    kotlin("jvm")
    application
    id("com.gradleup.shadow") version "9.4.3"
    id("org.jetbrains.kotlinx.kover")
    id("org.graalvm.buildtools.native") version "1.1.3"
}

group = "com.adoptu"
version = "1.0.0"

// SCSS/CSS, page templates (kotlinx.html), and the compiled JS bundle all moved to :frontend's
// generateSite task (frontend/src/jvmMain/kotlin/com/adoptu/site/SiteGenerator.kt) - the backend
// is API-only now (no more UIRoutes.kt / com.adoptu.pages / /static route), so it has nothing
// left to compile Sass for.

// Env var first (terminal builds), falling back to a Gradle property of the same name
// (IDE-launched Gradle daemons don't inherit shell rc files) -- same helper Mazmobi/Bitakore/
// Find-u use for their own GitHub-Packages-consumed Universaliun libraries.
fun credential(name: String): String? = System.getenv(name) ?: findProperty(name) as String?

repositories {
    mavenCentral()

    // EmailKit (SMTP/SES send adapters) -- see Libraries/EmailKit/README.md. Requires a GitHub
    // PAT with `read:packages` scope: set GITHUB_ACTOR / PAYMENT_KIT_TOKEN in the environment
    // (same variable names EmailKit itself publishes with -- matches Mazmobi/Bitakore/Find-u's
    // identical setup). content{} scopes this repository to only the email group.
    maven {
        name = "EmailKitGitHubPackages"
        url = uri("https://maven.pkg.github.com/ULibraries/EmailKit")
        credentials {
            username = credential("GITHUB_ACTOR")
            password = credential("PAYMENT_KIT_TOKEN")
        }
        content { includeGroup("com.universaliun.email") }
    }

    // RateLimitKit (generic rate-limit/throttle primitive) -- see Libraries/RateLimitKit/README.md.
    // Same GITHUB_ACTOR/PAYMENT_KIT_TOKEN credential pair as EmailKit above; content{} scopes this
    // repository to only the ratelimit group.
    maven {
        name = "RateLimitKitGitHubPackages"
        url = uri("https://maven.pkg.github.com/ULibraries/RateLimitKit")
        credentials {
            username = credential("GITHUB_ACTOR")
            password = credential("PAYMENT_KIT_TOKEN")
        }
        content { includeGroup("com.universaliun.ratelimit") }
    }

    // AuthKit (login/JWT/OAuth/WebAuthn passkey/magic-link auth engine, generic RBAC
    // Role/Resource/PermissionSet) -- see Libraries/AuthKit/README.md. Uses AUTH_KIT_TOKEN
    // (AuthKit's own publish credential, distinct from PAYMENT_KIT_TOKEN above) with the same
    // GITHUB_ACTOR; content{} scopes this repository to only the auth group.
    maven {
        name = "AuthKitGitHubPackages"
        url = uri("https://maven.pkg.github.com/ULibraries/AuthKit")
        credentials {
            username = credential("GITHUB_ACTOR")
            password = credential("AUTH_KIT_TOKEN")
        }
        content { includeGroup("com.universaliun.auth") }
    }

    // StorageKit (object storage) -- see Libraries/StorageKit/README.md and Bitakore's
    // docs/StorageKitExtraction.md. GITHUB_ACTOR / STORAGE_KIT_TOKEN in the environment. Artifact
    // ids are "storagekit-backend"/"storagekit-common", not the bare "backend"/"common" every
    // other Kit uses -- see StorageKit's own build.gradle.kts comment for why.
    maven {
        name = "StorageKitGitHubPackages"
        url = uri("https://maven.pkg.github.com/ULibraries/StorageKit")
        credentials {
            username = credential("GITHUB_ACTOR")
            password = credential("STORAGE_KIT_TOKEN")
        }
        content { includeGroup("com.universaliun.storagekit") }
    }

    // ImageKit (pure-JVM JPEG/PNG codecs, zero java.awt/javax.imageio) -- see
    // Libraries/ImageKit/README.md. Extracted after javax.imageio's PNG path crashed GraalVM
    // native-image at runtime (NoClassDefFoundError: java/awt/GraphicsEnvironment) the first time
    // a PNG was uploaded -- see ImageCompressor.kt's own comment for the full incident writeup.
    maven {
        name = "ImageKitGitHubPackages"
        url = uri("https://maven.pkg.github.com/ULibraries/ImageKit")
        credentials {
            username = credential("GITHUB_ACTOR")
            password = credential("IMAGE_KIT_TOKEN")
        }
        content { includeGroup("com.universaliun.imagekit") }
    }

    // DataFormatsKit (generated JSON codecs, `dataformatskit-jvm` + its Helidon 4 media support
    // `dataformatskit-helidon-media`) -- see Libraries/DataFormatsKit/README.md and
    // docs/codegen-consumers.md / docs/helidon.md in that repo. GITHUB_ACTOR /
    // DATA_FORMATS_KIT_TOKEN in the environment; content{} scopes this repository to the bare
    // com.universaliun group (DataFormatsKit's own coordinates, distinct from every other Kit's
    // com.universaliun.<kit> subgroup above).
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

// EmailKit is consumed as a `1.0-SNAPSHOT` ("changing") dependency -- same reasoning as the other
// Universaliun libraries' identical setting: it's an internal library published by hand, so extend
// Gradle's default 24h changing-module revalidation window to reuse the local dependency cache
// instead of re-checking GitHub Packages on every build. `--refresh-dependencies` forces a pull.
configurations.all {
    resolutionStrategy.cacheChangingModulesFor(30, "days")
}

kotlin {
    jvmToolchain(25)
}

val helidonVersion = "4.5.0"
val jacksonKotlinVersion = "2.22.0"
val exposedVersion = "1.3.1"
val postgresVersion = "42.7.12"
val koinVersion = "4.2.2"
val kotlinxDatetimeVersion = "0.8.0"
val byteBuddyVersion = "1.18.10"
val kotestVersion = "6.2.1"
val playwrightVersion = "1.61.0"

dependencies {
    // runtime / implementation
    implementation(project(":common"))
    implementation("io.helidon.webserver:helidon-webserver:$helidonVersion")
    implementation("io.helidon.webserver:helidon-webserver-static-content:$helidonVersion")
    // Jackson stays: fallback MediaSupport for PagedResult<T> (a generic wrapper the codegen
    // does not support - see JsonSupport.kt's comment) and for the request DTOs declared
    // directly in routes/AuthRoutes.kt + routes/UsersRoutes.kt, plus multipart image/video
    // upload parsing (MultiPartSupport, unrelated to JSON).
    implementation("io.helidon.http.media:helidon-http-media-jackson:$helidonVersion")
    implementation("io.helidon.http.media:helidon-http-media-multipart:$helidonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonKotlinVersion")
    // DataFormatsKit: generated JSON codecs (`@JsonDecodable`/`@JsonEncodable`, dfk-codegen/)
    // replacing Jackson for backend/{dto,web} - see JsonSupport.kt.
    implementation("com.universaliun:dataformatskit-jvm:0.2.0")
    implementation("com.universaliun:dataformatskit-helidon-media:0.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0")
    implementation("com.typesafe:config:1.4.5")

    implementation("com.webauthn4j:webauthn4j-core:0.31.7.RELEASE")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.checkerframework:checker-qual:4.2.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-migration-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:$exposedVersion")

    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")

    implementation("ch.qos.logback:logback-classic:1.5.37")

    implementation(platform("software.amazon.awssdk:bom:2.46.18"))
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "net.bytebuddy")
    }

    // Object storage's actual PutObject/DeleteObject calls moved onto StorageKit (Libraries/
    // StorageKit, see Bitakore's docs/StorageKitExtraction.md) -- AppModule.kt still builds the
    // S3Client itself (EcsTaskCredentialsProvider is a GraalVM-native-image-safe workaround
    // StorageKit's own createS3Client() doesn't know about; see that file's comment) and passes
    // it into StorageKit's S3ObjectStorageAdapter directly, so software.amazon.awssdk:s3 above
    // stays a direct dependency regardless.
    implementation("com.universaliun.storagekit:storagekit-backend:1.0.0")
    // Pure-JVM JPEG/PNG encode/decode/resize for ImageCompressor.kt -- see that file's comment
    // and Libraries/ImageKit/README.md for why (GraalVM native-image + javax.imageio/java.awt
    // don't mix safely).
    implementation("com.universaliun.imagekit:imagekit-common:1.0.1")
    implementation("software.amazon.awssdk:ses") {
        exclude(group = "net.bytebuddy")
    }
    implementation("software.amazon.awssdk:sesv2") {
        exclude(group = "net.bytebuddy")
    }
    implementation("software.amazon.awssdk:sns") {
        exclude(group = "net.bytebuddy")
    }

    implementation("com.password4j:password4j:1.8.4")

    // Transactional email (SMTP in dev via Mailpit, SES in prod) -- replaces the previous
    // hand-rolled SesEmailAdapter/commons-email combo. See di/EmailSenderConfig.kt.
    // Artifact renamed from bare "backend" to "emailkit-backend" -- EmailKit, RateLimitKit and
    // AuthKit's backend modules all used to publish the same generic "backend" artifactId, which
    // collided as lib/backend-1.0-SNAPSHOT.jar in distTar/distZip once 2+ were combined here.
    implementation("com.universaliun.email:emailkit-backend:1.0.0")

    // Daily-resend throttles (password reset, magic link, email verification) -- see
    // services/PasswordService.kt, MagicLinkService.kt, EmailVerificationService.kt.
    implementation("com.universaliun.ratelimit:ratelimitkit-backend:1.0.0")

    // Login/register/refresh/passkey/magic-link/OAuth/password-reset auth engine -- replaces
    // AuthRoutes.kt's own hand-rolled session/token logic. See adapters/authkit/.
    implementation("com.universaliun.auth:authkit-backend:1.3.0")

    // test
    testImplementation(kotlin("test"))
    testImplementation("net.bytebuddy:byte-buddy:$byteBuddyVersion")
    testImplementation("net.bytebuddy:byte-buddy-agent:$byteBuddyVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.11.0")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.helidon.webserver.testing.junit5:helidon-webserver-testing-junit5:$helidonVersion")
    testImplementation("io.helidon.webclient:helidon-webclient:$helidonVersion")
    testImplementation("com.h2database:h2:2.4.240")
    // Builds a real BCrypt-format hash as a test fixture for AdoptuPasswordReusePolicyAdapterTest
    // -- AuthKit's own PasswordHasher (Argon2id) is `internal` to that module, but verifyPassword()
    // accepts BCrypt hashes too (its documented legacy-format support).
    testImplementation("at.favre.lib:bcrypt:0.10.2")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:localstack:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("com.microsoft.playwright:playwright:$playwrightVersion")
}

// DataFormatsKit JSON codegen: the `dfk-codegen` composite build (root settings.gradle.kts) runs
// KSP over backend/src/main/kotlin/com/adoptu/{dto/**,web/JsonResponses.kt} in an isolated
// Kotlin 2.3.10 compile (this module is on Kotlin 2.4.0, which has no KSP release yet - see
// docs/codegen-consumers.md section 3 in the DataFormatsKit repo). The generated
// decodeAsFoo()/Foo.encodeToJson() extensions land under
// dfk-codegen/scan/build/generated/ksp/main/kotlin and are added here as an ordinary source dir.
val generateJsonCodecs by tasks.registering {
    dependsOn(gradle.includedBuild("dfk-codegen").task(":scan:kspKotlin"))
}
kotlin.sourceSets.main {
    kotlin.srcDir(rootProject.projectDir.resolve("dfk-codegen/scan/build/generated/ksp/main/kotlin"))
}
tasks.named("compileKotlin") {
    dependsOn(generateJsonCodecs)
}

application {
    mainClass.set("com.adoptu.ApplicationKt")
}

graalvmNative {
    metadataRepository {
        enabled.set(true)
        // Pinned rather than left on default/"latest" resolution - unpinned, a container
        // build with no prior Gradle cache re-resolves and re-downloads this repository
        // fresh, and an in-flight network hiccup or the two-invocation :backend:jar then
        // :backend:nativeCompile split (see Dockerfile's comment on why they're separate)
        // can leave a partially-extracted, "missing schema files" cache behind that a
        // later invocation trusts as complete. Version verified directly against the
        // release archive (schemas/library-and-framework-list-schema-v1.0.0.json and
        // schemas/metadata-library-index-schema-v2.3.0.json both present).
        version.set("1.0.11")
    }
    binaries {
        named("main") {
            imageName.set("adoptu-backend")
            // Helidon's WebServer has no Netty-style native-image incompatibility, so the
            // production entry point works directly - no separate native main() needed.
            mainClass.set("com.adoptu.ApplicationKt")
            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(25))
                    // Defaults to "Oracle": the container-registry.oracle.com/graalvm/native-image
                    // builder image's own JDK reports java.vendor=Oracle Corporation (the string
                    // "GraalVM" only shows up in java.vendor.version there), so matching("GraalVM")
                    // never matches it and Gradle silently auto-provisions a graalvm_community
                    // toolchain via Foojay instead - whose native-image came out as a 0-byte,
                    // non-executable stub on at least one build host (Exec failed, error: 13
                    // Permission denied). Override with -PnativeToolchainVendor=GraalVM for a local
                    // GraalVM Community installation instead of the Oracle Docker builder.
                    vendor.set(JvmVendorSpec.matching(findProperty("nativeToolchainVendor") as? String ?: "Oracle"))
                }
            )
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            // -O3: full optimization. Oracle GraalVM's -O3 (the builder image in the root
            // Dockerfile) additionally uses ML-inferred profiles, which measurably beats
            // Community's -O3 - worth the longer build for a task that runs at steady state far
            // longer than it ever spends compiling.
            buildArgs.add("-O3")
            // ECS Fargate x86_64 hosts are Xeon Platinum / EPYC - both guarantee AVX2 (x86-64-v3
            // baseline). Do NOT target v4/AVX-512: Fargate doesn't guarantee it across the x86 fleet.
            buildArgs.add("-march=x86-64-v3")
            // GC choice is BUILD-time for native-image, so it's a Gradle property, not a runtime
            // flag: -PnativeGc=serial|G1, defaulting to serial (this task's current 0.25-0.5 vCPU /
            // 512MB-1GB Fargate size -- see "Runtime profile by task size" in AGENTS.md). Below 1
            // vCPU, G1's concurrent marking/refinement threads need a spare core to run alongside
            // the mutator (which this task doesn't have) plus ~50-100MB of region-bookkeeping RSS
            // this task's memory budget can't spare. Serial has no concurrent threads and the
            // smallest footprint of any native-image collector. Once the task grows to 1-2 vCPU,
            // the Dockerfile's NATIVE_GC build arg flips this to G1 without touching this file.
            buildArgs.add("--gc=" + (findProperty("nativeGc") ?: "serial"))
            // ImageCompressor uses javax.imageio, which touches java.awt.Toolkit at class
            // init. Without this, Toolkit tries the X11-backed libawt_xawt.so - the
            // oraclelinux:10-slim runtime image has no X11 libraries installed at all, so
            // that would fail differently even once libawt.so itself is present. Baking
            // this in at build time (rather than as a runtime flag) means it can't be
            // forgotten by a future ENTRYPOINT change.
            buildArgs.add("-Djava.awt.headless=true")
            // Dynamically linked against glibc (the default) - the ghcr.io/graalvm/
            // native-image-community builder image has no musl cross-toolchain installed,
            // so --static --libc=musl fails with "x86_64-linux-musl-gcc not found". The
            // runtime stage uses oraclelinux:10-slim, matching the builder's own OS/glibc
            // (Oracle Linux 10.1, glibc 2.39) so the binary runs without ABI mismatches.
            quickBuild.set(true)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxParallelForks = Runtime.getRuntime().availableProcessors()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    exclude("com/adoptu/e2e/**")
    // Testcontainers tests need a running Docker daemon; they run via integrationTest task instead.
    exclude("**/*IT.class")
    exclude("**/ApplicationIntegrationTest.class")
    exclude("**/ApplicationContainerTest.class")
    exclude("**/SheltersRoutesE2ETest.class")
}

tasks.register<Exec>("dockerUp") {
    group = "docker"
    description = "Start Docker containers for integration tests"
    workingDir = rootProject.rootDir
    environment["DOCKER_HOST"] = "unix:///run/docker.sock"
    commandLine("sh", "-c", "docker compose up -d")
}

tasks.register<Exec>("dockerDown") {
    group = "docker"
    description = "Stop Docker containers for integration tests"
    workingDir = rootProject.rootDir
    commandLine("docker", "compose", "down", "-v")
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Run integration tests with Docker"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    maxParallelForks = Runtime.getRuntime().availableProcessors()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.base/java.math=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
        "--add-opens", "jdk.unsupported/sun.misc=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/sun.security.ssl=ALL-UNNAMED",
        "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
        "-XX:+EnableDynamicAgentLoading",
        "-Dkotest.framework.classpath.scanning.config.disable=true",
        "-Dkotest.framework.classpath.scanning.autoscan.disable=true"
    )
    systemProperty("jdk.module.illegalAccess", "permit")
    systemProperty("jdk.suppressUnsupportedWarningWarnings", "true")
    dependsOn("dockerUp")
    // tasks.withType<Test> above excludes *IT/Application*Test classes from every Test task
    // (so the fast `test` task skips Docker-only suites). Without clearing that here, this
    // task's own includeTestsMatching(".*IT") filter has nothing left to match and it
    // silently runs zero tests.
    setExcludes(emptySet())
    filter {
        // Gradle's TestFilter uses '*'-glob matching, not regex -- ".*IT" requires the name
        // to literally start with a dot and never matches anything; "*IT" is the correct glob.
        includeTestsMatching("*IT")
    }
}

kover {
    reports {
        filters {
            excludes {
                // entrypoint / bootstrap wiring, exercised by ApplicationIntegrationTest+ApplicationContainerTest
                // (Docker-only IT suite) rather than unit coverage
                classes("com.adoptu.ApplicationKt", "com.adoptu.ApplicationKt$*")
            }
        }
        verify {
            rule("overall minimum") {
                minBound(95)
            }
            rule("per-class minimum") {
                groupBy = GroupingEntityType.CLASS
                minBound(90)
            }
        }
    }
}

tasks.register<Exec>("e2eTest") {
    group = "verification"
    description = "Run E2E tests with Playwright in Docker"
    workingDir = rootProject.rootDir
    val projectDir = rootProject.rootDir.absolutePath
    val gradleHome = System.getenv("GRADLE_USER_HOME") ?: "${System.getProperty("user.home")}/.gradle"
    commandLine("docker", "run", "--rm",
        "--network", "host",
        "-v", "$projectDir:/workspace",
        "-v", "$gradleHome:/root/.gradle",
        "-w", "/workspace",
        "-e", "CI=true",
        "-e", "GRADLE_USER_HOME=/root/.gradle",
        "mcr.microsoft.com/playwright/java:v1.58.0-jammy",
        "bash", "-lc",
        "cd /workspace && ./gradlew :backend:test --tests 'com.adoptu.e2e.*' --no-daemon"
    )
}
