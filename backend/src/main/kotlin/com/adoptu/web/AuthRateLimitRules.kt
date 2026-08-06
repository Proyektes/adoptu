package com.adoptu.web

import com.universaliun.ratelimit.common.RateLimitPolicy
import kotlin.time.Duration.Companion.minutes

/**
 * Risk-calibrated policy table for `AuthRoutes.kt`'s endpoints -- unlike Bitakore/Locate-u
 * (which mount AuthKit's own route-registration functions wholesale) adopt-u writes every route
 * `Handler` itself, so each rule here is applied inline in the handler via
 * [com.universaliun.ratelimit.backend.adapter.`in`.web.enforceIpRateLimit]/
 * [com.universaliun.ratelimit.backend.adapter.`in`.web.enforceAccountRateLimit] rather than a
 * path-matching filter, getting full IP-*and*-account dual-keying where an account identity is
 * available -- the same shape Mazmobi's `AuthRateLimitRules` already proved out.
 *
 * `login-with-password` deliberately has no entry here: it already has its own, better-targeted
 * account lockout (`PasswordService.isLoginRateLimited`/`recordLoginAttempt` -- 5 *failed*
 * attempts / 15 min, DB-backed) that only counts actual failures, not every attempt. [LOGIN_IP]
 * adds the missing IP dimension alongside it (an attacker spraying many different accounts from
 * one IP isn't caught by a per-account failed-attempt counter), without touching that mechanism.
 *
 * Windows/limits calibrated off Mazmobi's own production rate-limit rules, the most mature prior
 * art in this codebase family.
 */
object AuthRateLimitRules {
    val LOGIN_IP = "auth:login:ip" to RateLimitPolicy(window = 15.minutes, maxEventsPerWindow = 10)

    // Generous (not the usual register-tier 3/window): unlike the other registration endpoints,
    // a legitimate caller can hit this one several times in one session (mistyped email retried,
    // "already registered"/"verification resent" branches probed) without it being abuse -- still
    // a meaningful throttle against automated signup spam at 10/window.
    val REGISTRATION_OPTIONS_IP = "auth:registration-options:ip" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 10)
    val REGISTRATION_OPTIONS_ACCOUNT = "auth:registration-options:account" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 10)

    val REGISTER_IP = "auth:register:ip" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 3)

    // 5, not the usual register-tier 3: a shared IP (household, office) can legitimately produce
    // several distinct-account signups in one window without it being abuse -- still meaningfully
    // tighter than the generous tiers above, since this is the actual account-creation action.
    val REGISTER_PASSWORD_IP = "auth:register-password:ip" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 5)
    val REGISTER_PASSWORD_ACCOUNT = "auth:register-password:account" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 3)

    /** Classic email-bomb vector -- tight, same tier as register. */
    val RESEND_VERIFICATION_IP = "auth:resend-verification:ip" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 3)

    /** Passkey ceremony generation, fully discoverable (no email input) -- IP only. */
    val ASSERTION_OPTIONS_IP = "auth:assertion-options:ip" to RateLimitPolicy(window = 5.minutes, maxEventsPerWindow = 10)

    /** Passkey login finish -- hardware-gated, throttle blunts scripted abuse. */
    val AUTHENTICATE_IP = "auth:authenticate:ip" to RateLimitPolicy(window = 5.minutes, maxEventsPerWindow = 10)

    /** Email-bombing vector. */
    val MAGIC_LINK_REQUEST_IP = "auth:magic-link:request:ip" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 3)
    val MAGIC_LINK_REQUEST_ACCOUNT = "auth:magic-link:request:account" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 3)

    /** Token consumption -- the token itself is the real gate, so this is generous. */
    val MAGIC_LINK_LOGIN_IP = "auth:magic-link:login:ip" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 10)

    /** Email-bombing + enumeration vector. */
    val FORGOT_PASSWORD_IP = "auth:forgot-password:ip" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 3)
    val FORGOT_PASSWORD_ACCOUNT = "auth:forgot-password:account" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 3)

    /** Token consumption -- the token itself is the real gate, so this is generous. */
    val RESET_PASSWORD_IP = "auth:reset-password:ip" to RateLimitPolicy(window = 10.minutes, maxEventsPerWindow = 10)
}
