package com.adoptu.pages

import com.adoptu.web.CspNonce
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Direct unit coverage for the auth-flow page builders in ForgotPasswordPage.kt.
 *
 * UIRoutesE2ETest already hits /forgot-password, /reset-password and /verify-email-change over
 * HTTP, but those handlers always pass an explicit NavParams, so the zero-arg default-parameter
 * bridge for each function (fun HTML.xxxPage(navParams: NavParams = NavParams())) never runs.
 * profileEmailVerificationPage() has no UI route at all (only /verify-contact-email... is wired
 * through a different flow) so it was entirely uncovered. Calling every function here directly,
 * both with defaults and explicit args, closes both gaps.
 */
class ForgotPasswordPageTest {

    @BeforeEach
    fun setNonce() {
        CspNonce.set("test-nonce")
    }

    @AfterEach
    fun clearNonce() {
        CspNonce.clear()
    }

    @Test
    fun `forgotPasswordPage renders with default NavParams`() {
        val html = createHTML().html { forgotPasswordPage() }
        assertTrue(html.contains("Forgot Password"))
        assertTrue(html.contains("Send Reset Link"))
        assertTrue(html.contains("Back to Login"))
        assertTrue(html.contains("common.js"))
    }

    @Test
    fun `forgotPasswordPage renders with authenticated NavParams`() {
        val html = createHTML().html {
            forgotPasswordPage(com.adoptu.routes.NavParams(isLoggedIn = true, isAdmin = true))
        }
        assertTrue(html.contains("Forgot Password"))
        assertTrue(html.contains("isLoggedInGlobal = true"))
    }

    @Test
    fun `resetPasswordPage renders with default NavParams`() {
        val html = createHTML().html { resetPasswordPage() }
        assertTrue(html.contains("Reset Password"))
        assertTrue(html.contains("New Password"))
        assertTrue(html.contains("Confirm Password"))
    }

    @Test
    fun `resetPasswordPage renders with authenticated NavParams`() {
        val html = createHTML().html {
            resetPasswordPage(com.adoptu.routes.NavParams(isLoggedIn = true, isRescuerOrAdmin = true))
        }
        assertTrue(html.contains("Reset Password"))
        assertTrue(html.contains("isLoggedInGlobal = true"))
    }

    @Test
    fun `emailChangeVerificationPage renders with default NavParams`() {
        val html = createHTML().html { emailChangeVerificationPage() }
        assertTrue(html.contains("Email Change"))
        assertTrue(html.contains("Verifying"))
    }

    @Test
    fun `emailChangeVerificationPage renders with authenticated NavParams`() {
        val html = createHTML().html {
            emailChangeVerificationPage(com.adoptu.routes.NavParams(isLoggedIn = true, isTemporalHomeOrAdmin = true))
        }
        assertTrue(html.contains("Email Change"))
        assertTrue(html.contains("isLoggedInGlobal = true"))
    }

    @Test
    fun `profileEmailVerificationPage renders with default NavParams`() {
        val html = createHTML().html { profileEmailVerificationPage() }
        assertTrue(html.contains("Verify Contact Email"))
        assertTrue(html.contains("Verifying"))
        assertTrue(html.contains("common.js"))
    }

    @Test
    fun `profileEmailVerificationPage renders with authenticated NavParams`() {
        val html = createHTML().html {
            profileEmailVerificationPage(com.adoptu.routes.NavParams(isLoggedIn = true, isAdmin = true, isRescuerOrAdmin = true, isTemporalHomeOrAdmin = true))
        }
        assertTrue(html.contains("Verify Contact Email"))
        assertTrue(html.contains("isLoggedInGlobal = true"))
    }
}
