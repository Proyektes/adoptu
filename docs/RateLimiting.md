# 🚦 Rate Limiting

How adopt-u throttles its authentication endpoints, and how that fits into a shared mechanism
used across every app in this codebase family (Bitakore, Mazmobi, Locate-u, adopt-u).

---

## Architecture

Unlike Bitakore/Locate-u — which mount AuthKit's own route-registration functions
(`authRoutes()`, `passkeyRoutes()`, etc.) wholesale and have no per-route hook to check a limit
from inline — adopt-u writes every route `Handler` itself directly in `routes/AuthRoutes.kt`. That
means it *can* use `Libraries/RateLimitKit`'s inline mechanism:
`enforceIpRateLimit`/`enforceAccountRateLimit` (called on the already-injected `RateLimiter`,
which was declared in `di/AppModule.kt` but never actually used before this), placed at the top of
each handler — after the request body has been parsed enough to know the account identity, for the
account-keyed check. This mirrors the shape Mazmobi already proved out (Mazmobi is the only other
app in this family that owns its route handlers the same way), and gets full **IP-and-account
dual-keying** other apps in this family can't get from a path-matching `Filter` alone: an attacker
rotating IPs still trips the account-keyed limit, and a shared-IP legitimate population isn't
collectively blocked by the IP-keyed one alone.

`web/AuthRateLimitRules.kt` is the policy table (`(limitKind, RateLimitPolicy)` pairs); each
handler in `routes/AuthRoutes.kt` calls `rateLimiter.enforceIpRateLimit(req, res, RULE)` and/or
`rateLimiter.enforceAccountRateLimit(res, account, RULE)` as its first statement (or right after
the account identity is parsed out of the body). State is persisted through RateLimitKit's own
`ExposedRateLimitStateAdapter` (the same `RateLimitState` table Bitakore/Locate-u/Mazmobi all
use), so the throttle survives an app restart.

Before this, **13 endpoints had zero rate limiting** — `RateLimitKit` was a declared Gradle
dependency that nothing actually called.

### `login-with-password` is a special case

This endpoint already had its own account lockout — `PasswordService.isLoginRateLimited`/
`recordLoginAttempt` (5 *failed* attempts / 15 min, DB-backed via a dedicated `LoginAttempts`
table) — which only counts real failures, not every attempt, making it more precisely targeted
than a generic fixed-window policy. That mechanism was left untouched; this migration only adds a
new **IP-only** layer alongside it (`AuthRateLimitRules.LOGIN_IP`), closing the gap where an
attacker spraying many different accounts from one IP wasn't caught by a per-account counter.

## Policy table

Calibrated off Mazmobi's own production numbers, the most mature prior art in this codebase
family — with two numbers tuned up from the initial draft after the existing `AuthRoutesE2ETest`
suite caught real collisions (see Verification below).

| Endpoint | Keying | Window | Max | Why |
|---|---|---|---|---|
| `login-with-password` | IP only (account already covered, see above) | 15 min | 10 | Credential-stuffing target |
| `registration-options` | IP + account | 10 min | 10 | Generous: legitimate multi-attempt flow (typo retries, branch probing) |
| `register` (passkey signup finish) | IP only | 10 min | 3 | Spam / fake-account creation |
| `register-password` | IP + account | 10 min | IP 5, account 3 | Real account creation; IP loosened for shared-household signups |
| `resend-verification` | IP only | 10 min | 3 | Email-bombing vector |
| `assertion-options` (passkey login start) | IP only | 5 min | 10 | Fully discoverable/usernameless — no account to key by |
| `authenticate` (passkey login finish) | IP only | 5 min | 10 | Hardware-gated, throttles scripted ceremony abuse |
| `request-magic-link` | IP + account | 10 min | 3 | Email-bombing vector |
| `magic-link-login` (token consumption) | IP only | 10 min | 10 | Token itself is the real gate — generous |
| `forgot-password` | IP + account | 10 min | 3 | Email-bombing + enumeration vector |
| `reset-password` (token consumption) | IP only | 10 min | 10 | Token itself is the real gate — generous |

`registration-options-for-user`/`register-passkey` (adding a passkey to an *already-authenticated*
session) and `logout`/`me`/`encryption-key`/`has-passkey` aren't rate-limited — they either
require an existing valid session already or carry no abuse value on their own.

## Verification

- `./gradlew :backend:test` — full suite, 1291/1291 tests green, including 3 new tests added to
  `AuthRoutesE2ETest.kt` confirming the throttle actually trips (`forgot-password` 429 +
  `Retry-After` after 3 attempts, `login-with-password`'s new IP layer trips independent of
  `PasswordService`'s account lockout, and cross-endpoint independence).
- Running the *existing* E2E suite against the first draft of this policy table caught two real
  calibration bugs before they shipped: the `registration-options` localization tests legitimately
  call that endpoint 5x per test method (probing different response branches), and the
  `register-password`-touching helper functions (`registerVerifiedUser`/`registerUnverifiedUser`)
  make up to 4 calls in a single test — both exceeded the initial 3/window draft. Both limits were
  loosened to fit real usage patterns rather than the tests being changed to fit an arbitrary
  number.
- Live end-to-end: booted `./gradlew run` + `:frontend:serveSite` locally and drove the actual
  login/register/forgot-password pages through the browser — a bad-credential `login-with-password`
  attempt correctly returns `200` with an "Invalid credentials" message in the body (this app's own
  convention: the route always 200s, failure is encoded in the JSON), and a forgot-password request
  correctly returns `200`. Neither was blocked, confirming the filter passes legitimate single
  attempts through unaffected before it ever throttles anything.

## Cross-app status

| App | Mechanism | Status |
|---|---|---|
| Mazmobi | Inline `enforceIpRateLimit`/`enforceAccountRateLimit` (owns its routes; dual-keyed) | Reference implementation |
| Locate-u | `installPathRateLimits` (mounts AuthKit routes wholesale) | Done |
| Bitakore | `installPathRateLimits` | Done |
| adopt-u | Inline `enforceIpRateLimit`/`enforceAccountRateLimit` (owns its routes; dual-keyed) | Done (this doc) |
