package com.adoptu.e2e

import com.adoptu.testsupport.TestServer
import com.adoptu.testsupport.TestServerHandle
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.atomic.AtomicBoolean

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseE2ETest {

    companion object {
        private val started = AtomicBoolean(false)
        private var handle: TestServerHandle? = null
        private var serverHost = "localhost"

        protected fun getBaseUrl(): String {
            val baseUrl = handle!!.baseUrl
            return if (serverHost != "localhost") {
                baseUrl.replaceFirst("localhost", serverHost)
            } else {
                baseUrl
            }
        }
    }

    protected lateinit var playwright: Playwright
    protected lateinit var browser: Browser
    protected lateinit var context: BrowserContext
    protected lateinit var page: Page

    protected val jsErrors = mutableListOf<String>()
    protected val consoleErrors = mutableListOf<String>()

    @BeforeAll
    fun setupServer() {
        if (!started.getAndSet(true)) {
            startTestServer()
        }
    }

    private fun startTestServer() {
        val hostOverride = System.getenv("PLAYWRIGHT_SERVER_HOST")
        if (!hostOverride.isNullOrBlank()) {
            serverHost = hostOverride
        }

        handle = TestServer.start()
    }

    @BeforeAll
    fun setupBrowser() {
        playwright = Playwright.create()

        val wsEndpoint = System.getenv("PLAYWRIGHT_WS_ENDPOINT")
        if (!wsEndpoint.isNullOrBlank()) {
            browser = playwright.chromium().connect(wsEndpoint)
        } else {
            val options = BrowserType.LaunchOptions().apply {
                headless = true
                args = listOf("--no-sandbox", "--disable-setuid-sandbox")
            }
            browser = playwright.chromium().launch(options)
        }

        context = browser.newContext()
        page = context.newPage()

        page.onPageError { jsErrors.add(it) }
        page.onConsoleMessage { msg ->
            if (msg.type() == "error") {
                consoleErrors.add(msg.text())
            }
        }
    }

    @AfterAll
    fun teardownBrowser() {
        page.close()
        context.close()
        browser.close()
        playwright.close()
        handle?.stop()
    }

    protected fun clearErrors() {
        jsErrors.clear()
        consoleErrors.clear()
    }

    protected fun navigateTo(path: String) {
        clearErrors()
        page.navigate("${getBaseUrl()}$path")
        page.waitForLoadState()
        page.waitForTimeout(500.0)
    }

    protected fun assertNoJsErrors() {
        kotlin.test.assertTrue(jsErrors.isEmpty())
    }

    protected fun assertNoConsoleErrors() {
        kotlin.test.assertTrue(consoleErrors.isEmpty())
    }

    protected fun assertNoErrors() {
        assertNoJsErrors()
        assertNoConsoleErrors()
    }
}
