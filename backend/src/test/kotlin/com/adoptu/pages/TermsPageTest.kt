package com.adoptu.pages

import com.adoptu.web.CspNonce
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Direct unit coverage for termsPage(). The E2E route test only exercises the function through
 * a live HTTP request; calling it here directly (a) also exercises the zero-arg default-parameter
 * bridge (fun HTML.termsPage(navParams: NavParams = NavParams())), which is otherwise never
 * invoked since UIRoutes always passes an explicit NavParams, and (b) guarantees every sequential
 * section of this branch-free HTML builder actually runs within a single deterministic test.
 */
class TermsPageTest {

    @BeforeEach
    fun setNonce() {
        CspNonce.set("test-nonce")
    }

    @AfterEach
    fun clearNonce() {
        CspNonce.clear()
    }

    @Test
    fun `termsPage renders with default NavParams`() {
        val html = createHTML().html { termsPage() }
        assertTrue(html.contains("Terms and Conditions"))
        assertTrue(html.contains("common.js"))
    }

    @Test
    fun `termsPage renders all major sections and lists`() {
        val html = createHTML().html { termsPage(com.adoptu.routes.NavParams()) }

        assertTrue(html.contains("Acceptance of Terms"))
        assertTrue(html.contains("Nature of Our Service"))
        assertTrue(html.contains("User Responsibilities"))
        assertTrue(html.contains("For Rescuers:"))
        assertTrue(html.contains("For Adopters:"))
        assertTrue(html.contains("Pet Listings and Information"))
        assertTrue(html.contains("Adoption Process"))
        assertTrue(html.contains("Limitation of Liability"))
        assertTrue(html.contains("Prohibited Activities"))
        assertTrue(html.contains("Account Termination"))
        assertTrue(html.contains("Modifications to Terms"))
        assertTrue(html.contains("admin@adopt-u.com"))

        // Rescuer responsibilities list
        assertTrue(html.contains("Provide accurate, truthful information about pets in your care"))
        assertTrue(html.contains("Never use the platform for commercial pet sales or breeding purposes"))

        // Adopter responsibilities list
        assertTrue(html.contains("Respect the rescuer's right to decline your adoption request"))

        // Adoption process steps
        assertTrue(html.contains("We strongly recommend documenting all agreements in writing"))

        // Liability list
        assertTrue(html.contains("Any actions taken by users outside of the platform after contact has been established"))

        // Prohibited activities list
        assertTrue(html.contains("Any illegal activities or violations of animal welfare laws"))
    }

    @Test
    fun `termsPage renders with authenticated NavParams`() {
        val html = createHTML().html {
            termsPage(com.adoptu.routes.NavParams(isLoggedIn = true, isAdmin = true, isRescuerOrAdmin = true, isTemporalHomeOrAdmin = true))
        }
        assertTrue(html.contains("Terms and Conditions"))
        assertTrue(html.contains("isLoggedInGlobal = true"))
    }
}
