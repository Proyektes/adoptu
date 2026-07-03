package com.adoptu.web

import io.helidon.webserver.http.Filter
import io.helidon.webserver.http.FilterChain
import io.helidon.webserver.http.RoutingRequest
import io.helidon.webserver.http.RoutingResponse
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AdoptU-Access")

/** Replaces Ktor's `install(CallLogging) { ... }` (plugins/Logging.kt). */
class AccessLogFilter : Filter {
    override fun filter(chain: FilterChain, req: RoutingRequest, res: RoutingResponse) {
        val path = req.path().path()
        if (path == "/health" || path.startsWith("/static") || path.startsWith("/css") || path.startsWith("/js")) {
            chain.proceed()
            return
        }
        val method = req.prologue().method().text()
        val start = System.nanoTime()
        chain.proceed()
        val durationMs = (System.nanoTime() - start) / 1_000_000
        logger.info("$method $path → ${res.status().code()} (${durationMs}ms)")
    }
}
