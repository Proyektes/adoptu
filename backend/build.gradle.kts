import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    kotlin("jvm")
    application
    id("com.gradleup.shadow") version "9.4.3"
    id("org.jetbrains.kotlinx.kover")
    id("org.graalvm.buildtools.native") version "1.1.3"
}

group = "com.adoptu"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

val helidonVersion = "4.5.0"
val jacksonKotlinVersion = "2.22.0"
val exposedVersion = "1.3.0"
val postgresVersion = "42.7.12"
val koinVersion = "4.2.2"
val kotlinxDatetimeVersion = "0.8.0"
val byteBuddyVersion = "1.18.10"
val kotestVersion = "6.2.1"
val playwrightVersion = "1.61.0"

dependencies {
    // runtime / implementation
    implementation("io.helidon.webserver:helidon-webserver:$helidonVersion")
    implementation("io.helidon.webserver:helidon-webserver-static-content:$helidonVersion")
    implementation("io.helidon.http.media:helidon-http-media-jackson:$helidonVersion")
    implementation("io.helidon.http.media:helidon-http-media-multipart:$helidonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonKotlinVersion")
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
    implementation("software.amazon.awssdk:ses") {
        exclude(group = "net.bytebuddy")
    }
    implementation("software.amazon.awssdk:sesv2") {
        exclude(group = "net.bytebuddy")
    }

    implementation("org.apache.commons:commons-email:1.6.0")
    implementation("com.password4j:password4j:1.8.4")

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
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:localstack:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("com.microsoft.playwright:playwright:$playwrightVersion")
}

application {
    mainClass.set("com.adoptu.ApplicationKt")
}

graalvmNative {
    metadataRepository {
        enabled.set(true)
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
                    vendor.set(JvmVendorSpec.matching("GraalVM"))
                }
            )
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
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
                classes("com.adoptu.ApplicationKt")
            }
        }
        verify {
            rule {
                minBound(95)
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
