package com.adoptu

import io.ktor.server.cio.EngineMain
import org.slf4j.LoggerFactory

private val nativeLogger = LoggerFactory.getLogger("AdoptU-Native")

// GraalVM native-image entry point (mainClass in backend/build.gradle.kts): same
// Application.module() as production, CIO engine instead of Netty (unsupported under native-image).
fun main(args: Array<String>) {
    val env = System.getenv("ADOPTU_ENV") ?: "dev"
    nativeLogger.info("Starting Adopt-U application (native-image, CIO engine) in $env environment")
    EngineMain.main(args)
}
