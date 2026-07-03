package com.adoptu.web

import org.koin.core.component.KoinComponent

/**
 * Replaces Ktor's `org.koin.ktor.ext.inject` (which resolved via the Application's attached
 * Koin instance). Plain koin-core's `KoinComponent.inject()` works the same way once Koin is
 * started globally in Application.kt - route registration functions do
 * `private val service: FooService by Deps.inject()`, resolved once when routes are built.
 */
object Deps : KoinComponent
