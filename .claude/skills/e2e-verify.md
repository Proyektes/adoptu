# E2E Verification Skill

End-to-end verification of the Adoptu app against test data.

## When to use
When the user asks to run e2e verification, end-to-end tests, or smoke tests against the running app.

## Steps

Run these steps in order:

### 1. Ensure services are running
```bash
curl -s http://localhost:8080/ -o /dev/null -w "%{http_code}"
curl -s http://localhost:8025/api/v1/messages | head -1
```
If the app (port 8080) is not running, tell the user to start it first.

### 2. Load test data
```bash
cd /path/to/adoptu && bash scripts/load_test_data.sh
```

### 3. Rebuild the frontend bundle if Kotlin/JS sources changed
Anything under `frontend/src/jsMain/kotlin/**` (including the shared
`LocationSearchFilters.kt` component and `I18n.kt`) only takes effect after:
```bash
./gradlew :frontend:jsBrowserProductionWebpack
```
This regenerates `backend/src/main/resources/static/js/common.js`. Editing the
Kotlin source alone has zero effect on a running backend until this runs and
the backend is restarted/reloaded.

### 4. Run the Playwright E2E tests
```bash
cd /path/to/adoptu && npx playwright test frontend/src/tests/e2e-verify.spec.ts --project=chromium --reporter=list
```

The suite is organized in 13 numbered `test.describe` blocks, in the order a
real user would hit them — extend the matching suite rather than appending a
new one at the end:
1. Test data (infra health)
2. Registration with password
3. Email verification (mailpit)
4. Admin login
5. Role-based logins
6. Search sections — country/city/zip filters, cross-page country persistence
7. Internationalization — all 5 languages translated, language switcher + persistence
8. Profile modification — name/country/password/email changes, all verified via DB reload
9. Pet management — add/edit/delete, image upload verified against storage
10. Adoption flow
11. Admin panel
12. Pet detail page — accuracy of rendered fields vs the API record
13. Logout

Suites 8 and 9 deliberately reuse specific seeded test users (`jorge.photo@email.com`,
`shelter.amigos@email.com`) for password/email-change tests because those
mutate credentials — picking a user still needed elsewhere in the suite (by its
original email/password) would break later tests. Check for new usages before
reassigning which user "belongs" to a mutating test.

### 5. Report results
Summarise pass/fail for each section.
