package com.adoptu.web

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.helidon.common.config.Config
import io.helidon.http.media.MediaContext
import io.helidon.http.media.jackson.JacksonSupport
import io.helidon.http.media.multipart.MultiPartSupport

/**
 * Jackson's DefaultPrettyPrinter differs from kotlinx.serialization's prettyPrint - what every
 * response body was shaped like before this migration, and what ported test assertions still
 * expect - in two ways: (1) it renders empty arrays/objects as "[ ]"/"{ }" instead of "[]"/"{}",
 * and (2) its default field separator is " : " (space on both sides) instead of ": " (space
 * after only). Fix both; non-empty container indentation is otherwise unaffected.
 */
private class CompactEmptyContainerPrettyPrinter :
    DefaultPrettyPrinter(Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER)) {

    override fun createInstance() = CompactEmptyContainerPrettyPrinter()

    override fun writeEndArray(g: JsonGenerator, nrOfValues: Int) {
        if (nrOfValues > 0) {
            super.writeEndArray(g, nrOfValues)
        } else {
            g.writeRaw(']')
        }
    }

    override fun writeEndObject(g: JsonGenerator, nrOfEntries: Int) {
        if (nrOfEntries > 0) {
            super.writeEndObject(g, nrOfEntries)
        } else {
            g.writeRaw('}')
        }
    }
}

/**
 * Replaces the Ktor ContentNegotiation/kotlinx.serialization plugin (plugins/Serialization.kt)
 * with Jackson, registered as Helidon's request/response media support.
 */
object JsonSupport {
    val objectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .setDefaultPrettyPrinter(CompactEmptyContainerPrettyPrinter())

    fun mediaContext(): MediaContext = MediaContext.builder()
        .addMediaSupport(JacksonSupport.create(objectMapper))
        .addMediaSupport(MultiPartSupport.create(Config.empty()))
        .build()
}
