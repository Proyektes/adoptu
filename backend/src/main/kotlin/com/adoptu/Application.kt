package com.adoptu

import com.adoptu.adapters.db.DatabaseFactory
import com.adoptu.config.AppConfig
import com.adoptu.di.appModule
import com.adoptu.routes.adminShelterRoutes
import com.adoptu.routes.adminSterilizationLocationRoutes
import com.adoptu.routes.adminUsersRoutes
import com.adoptu.routes.authRoutes
import com.adoptu.routes.petsRoutes
import com.adoptu.routes.photographerRoutes
import com.adoptu.routes.shelterRoutes
import com.adoptu.routes.sterilizationLocationRoutes
import com.adoptu.routes.temporalHomeRoutes
import com.adoptu.routes.uiRoutes
import com.adoptu.routes.userShelterRoutes
import com.adoptu.routes.userSterilizationLocationRoutes
import com.adoptu.routes.usersRoutes
import com.adoptu.services.crypto.CryptoService
import com.adoptu.web.AccessLogFilter
import com.adoptu.web.JsonSupport
import io.helidon.http.Status
import io.helidon.webserver.WebServer
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRouting
import io.helidon.webserver.staticcontent.StaticContentService
import org.koin.core.context.startKoin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AdoptU")

fun main() {
    val config = AppConfig.load()
    val env = config.propertyOrNull("env")?.getString() ?: "dev"
    logger.info("Starting Adopt-U application (Helidon Níma) in $env environment")

    startKoin {
        slf4jLogger()
        modules(appModule(config))
    }

    DatabaseFactory.init(config)
    CryptoService.initialize()

    val port = config.propertyOrNull("ktor.deployment.port")?.getString()?.toIntOrNull() ?: 8080
    val server = WebServer.builder()
        .port(port)
        .mediaContext(JsonSupport.mediaContext())
        .routing(::configureRouting)
        .build()
        .start()

    logger.info("Adopt-U listening on port ${server.port()}")
}

internal fun configureRouting(routing: HttpRouting.Builder) {
    routing.addFilter(AccessLogFilter())

    routing.error(io.helidon.http.NotFoundException::class.java) { _, res, _ ->
        res.status(Status.NOT_FOUND_404).send()
    }
    routing.error(io.helidon.http.HttpException::class.java) { _, res, ex ->
        // Helidon's static content handler uses this to short-circuit conditional
        // requests (e.g. If-None-Match -> 304) - it carries its own intended status
        // and headers and must not fall through to the generic 500 handler below.
        ex.headers().forEach { res.header(it) }
        res.status(ex.status()).send()
    }
    routing.error(Throwable::class.java) { _, res, cause ->
        logger.error("Unhandled exception", cause)
        res.status(Status.INTERNAL_SERVER_ERROR_500).send()
    }

    routing.register("/static", StaticContentService.builder("static").build())
    routing.get("/health", Handler { _, res -> res.send(mapOf("status" to "ok")) })

    routing.uiRoutes()
    routing.authRoutes()
    routing.petsRoutes()
    routing.usersRoutes()
    routing.adminUsersRoutes()
    routing.photographerRoutes()
    routing.temporalHomeRoutes()
    routing.shelterRoutes()
    routing.adminShelterRoutes()
    routing.sterilizationLocationRoutes()
    routing.adminSterilizationLocationRoutes()
    routing.userShelterRoutes()
    routing.userSterilizationLocationRoutes()
}
