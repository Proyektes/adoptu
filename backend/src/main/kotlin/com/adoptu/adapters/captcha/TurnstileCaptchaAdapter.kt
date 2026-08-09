package com.adoptu.adapters.captcha

import com.adoptu.ports.CaptchaPort
import com.adoptu.web.JsonSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

private val logger = LoggerFactory.getLogger("TurnstileCaptchaAdapter")

/**
 * Cloudflare Turnstile (https://developers.cloudflare.com/turnstile/) - free, privacy-friendly
 * CAPTCHA, embedded client-side via a plain <script> tag (no npm package, matching this project's
 * "no Node.js" convention - see frontend page templates' report-urgent widget).
 */
class TurnstileCaptchaAdapter(
    private val secretKey: String,
    private val verifyUrl: String = "https://challenges.cloudflare.com/turnstile/v0/siteverify",
) : CaptchaPort {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    override suspend fun verify(token: String, remoteIp: String?): Boolean = withContext(Dispatchers.IO) {
        val params = buildString {
            append("secret=${encode(secretKey)}")
            append("&response=${encode(token)}")
            if (remoteIp != null) append("&remoteip=${encode(remoteIp)}")
        }

        val request = HttpRequest.newBuilder(URI.create(verifyUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(params))
            .build()

        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val result = JsonSupport.objectMapper.readTree(response.body())
            result["success"]?.asBoolean() ?: false
        } catch (e: Exception) {
            logger.error("Turnstile verification failed", e)
            false
        }
    }
}
