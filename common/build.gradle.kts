plugins {
    kotlin("multiplatform") version "2.4.0"
}

group = "com.adoptu"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
    jvm()
    js(IR) {
        browser()
        nodejs()
    }
    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
