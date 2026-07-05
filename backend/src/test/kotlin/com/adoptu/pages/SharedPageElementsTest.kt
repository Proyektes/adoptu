package com.adoptu.pages

import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.html
import kotlinx.html.nav
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Covers small page-builder helpers that aren't reached through any HTTP route in production
 * (DIV.languageDropdown(), guestNav(), magicLinkLoginPage()) plus the default-argument bridge
 * methods for commonScripts()/commonNav() that only get generated/invoked when called with
 * zero arguments -- every route handler always passes explicit args, so these otherwise never run.
 */
class SharedPageElementsTest {

    @Test
    fun `DIV languageDropdown renders all language options`() {
        val html = createHTML().div { languageDropdown() }
        assertTrue(html.contains("lang-dropdown"))
        assertTrue(html.contains("English"))
        assertTrue(html.contains("中文"))
    }

    @Test
    fun `NAV guestNav renders login and register links`() {
        val html = createHTML().nav { guestNav() }
        assertTrue(html.contains("/login"))
        assertTrue(html.contains("/register"))
        assertTrue(html.contains("nav-donate"))
    }

    @Test
    fun `commonScripts renders with default isLoggedIn`() {
        val html = createHTML().body { commonScripts() }
        assertTrue(html.contains("common.js"))
        assertTrue(html.contains("isLoggedInGlobal = false"))
    }

    @Test
    fun `commonNav renders with all default params`() {
        val html = createHTML().nav { commonNav() }
        assertTrue(html.contains("nav-login"))
        assertTrue(html.contains("nav-register"))
    }

    @Test
    fun `magicLinkLoginPage renders verifying message`() {
        val html = createHTML().html { magicLinkLoginPage() }
        assertTrue(html.contains("Email Link Login"))
        assertTrue(html.contains("Verifying"))
        assertTrue(html.contains("common.js"))
    }
}
