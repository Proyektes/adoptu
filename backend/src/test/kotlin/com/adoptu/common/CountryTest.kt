package com.adoptu.common

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountryTest {

    @Test
    fun `fromDisplayName returns null for blank input`() {
        assertNull(Country.fromDisplayName(null))
        assertNull(Country.fromDisplayName(""))
        assertNull(Country.fromDisplayName("   "))
    }

    @Test
    fun `fromDisplayName matches an exact display name`() {
        assertEquals(Country.UNITED_STATES, Country.fromDisplayName("United States"))
    }

    @Test
    fun `fromDisplayName falls back to normalized fuzzy matching when the exact name does not match`() {
        // "MEXICO" isn't an exact match for the enum's displayName ("Mexico"), so this only
        // resolves via the normalized (case/diacritic-insensitive) lookup fallback.
        assertEquals(Country.MEXICO, Country.fromDisplayName("MEXICO"))
        // Accented input normalizes to the same key as the unaccented display name.
        assertEquals(Country.MEXICO, Country.fromDisplayName("México"))
    }

    @Test
    fun `fromDisplayName returns null for an unknown country`() {
        assertNull(Country.fromDisplayName("Narnia"))
    }

    @Test
    fun `fromIso2 returns null for blank input`() {
        assertNull(Country.fromIso2(null))
        assertNull(Country.fromIso2(""))
        assertNull(Country.fromIso2("   "))
    }

    @Test
    fun `fromIso2 matches a known alpha-2 code regardless of case or surrounding whitespace`() {
        assertEquals(Country.UNITED_STATES, Country.fromIso2("US"))
        assertEquals(Country.UNITED_STATES, Country.fromIso2("us"))
        assertEquals(Country.UNITED_STATES, Country.fromIso2(" us "))
    }

    @Test
    fun `fromIso2 returns null for an unknown code`() {
        assertNull(Country.fromIso2("ZZ"))
    }
}
