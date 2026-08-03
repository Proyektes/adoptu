package com.adoptu.site

import com.adoptu.site.pages.*
import kotlinx.html.HTML
import kotlinx.html.html
import kotlinx.html.stream.appendHTML
import java.io.File
import kotlin.system.exitProcess

// Renders every page in com.adoptu.site.pages to a static .html file under <siteOutDir>, always
// with the default (guest) NavParams() - a static file has no per-request session to render
// against, so CommonModule.initAuthNav() (frontend/Common.kt) resolves the real auth state
// client-side via GET /api/auth/me after load. Two pages (pet-detail, temporal-home-detail) get
// one file each even though their real routes carry a path param (/pet/{id},
// /temporal-home/{id}) - the id is read from window.location.pathname client-side
// (PetDetailPageModule/TemporalHomeDetailPageModule already do this), not baked into the HTML, so
// serving them for any id just needs a URL rewrite (see rewrites() below / scripts/serve_site.py).
fun main(args: Array<String>) {
    if (args.size != 3) {
        System.err.println("usage: SiteGenerator <cssOutDir> <jsOutDir> <siteOutDir>")
        exitProcess(1)
    }
    val (cssOutDir, jsOutDir, siteOutDirPath) = args
    val siteOutDir = File(siteOutDirPath)
    val staticCssDir = File(siteOutDir, "static/css").apply { mkdirs() }
    val staticJsDir = File(siteOutDir, "static/js").apply { mkdirs() }

    copyMatching(File(cssOutDir), staticCssDir) { it.extension == "css" }
    copyMatching(File(jsOutDir), staticJsDir) { it.extension == "js" || it.name.endsWith(".js.map") }

    val pages: Map<String, HTML.() -> Unit> = linkedMapOf(
        "index" to { indexPage() },
        "login" to { loginPage() },
        "register" to { registerPage() },
        "photographers" to { photographersPage() },
        "pet-food" to { petFoodPage() },
        "pet-detail" to { petDetailPage() },
        "pets" to { petsPage() },
        "my-pets" to { myPetsPage() },
        "profile" to { profilePage() },
        "admin" to { adminPage() },
        "admin-shelters" to { adminSheltersPage() },
        "privacy" to { privacyPage() },
        "terms" to { termsPage() },
        "temporal-home" to { temporalHomeProfilePage() },
        "temporal-homes" to { temporalHomesSearchPage() },
        "temporal-home-block" to { temporalHomeBlockPage() },
        "temporal-home-detail" to { temporalHomeDetailPage() },
        "shelters" to { sheltersPage() },
        "sterilization-locations" to { sterilizationLocationsPage() },
        "admin-sterilization-locations" to { adminSterilizationLocationsPage() },
        "verify" to { emailVerificationPage() },
        "verify-email" to { emailVerificationPage() },
        "forgot-password" to { forgotPasswordPage() },
        "reset-password" to { resetPasswordPage() },
        "magic-link-login" to { magicLinkLoginPage() },
        "verify-email-change" to { emailChangeVerificationPage() },
        "verify-profile-email" to { profileEmailVerificationPage() },
    )

    for ((name, render) in pages) {
        val html = buildString { appendHTML().html(block = render) }
        File(siteOutDir, "$name.html").writeText("<!DOCTYPE html>\n$html")
    }

    File(siteOutDir, "serve.json").writeText(SERVE_JSON)

    println("Generated ${pages.size} pages + static assets into $siteOutDir")
}

private fun copyMatching(from: File, into: File, matches: (File) -> Boolean) {
    val files = from.listFiles() ?: return
    for (f in files) {
        if (f.isFile && matches(f)) f.copyTo(File(into, f.name), overwrite = true)
    }
}

// Single source of truth for the "clean URL" + dynamic-path rewrites, read by both
// scripts/serve_site.py (local dev) and (eventually) the CloudFront Function that does the same
// job in production - see infra/cloudfront.tf.
private val SERVE_JSON = """
{
  "cleanUrls": true,
  "rewrites": [
    { "source": "^/pet/[0-9]+${'$'}", "destination": "/pet-detail.html" },
    { "source": "^/temporal-home/[0-9]+${'$'}", "destination": "/temporal-home-detail.html" }
  ]
}
""".trimIndent()
