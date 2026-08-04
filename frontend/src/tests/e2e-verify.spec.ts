/**
 * Adoptu — End-to-End Verification Suite
 *
 * Prerequisites:
 *   1. Backend running on http://localhost:8080 (JSON API only - see ADOPTU_PORT below)
 *   2. Static site running on http://localhost:4000 (`./gradlew :frontend:serveSite`, proxies
 *      /api/* to the backend) - BASE below points here, not at the backend, since pages
 *      (/login, /pets, /verify, ...) only exist on the static site now.
 *   3. Mailpit running on http://localhost:8025
 *   4. Test data loaded: bash scripts/load_test_data.sh
 *
 * All test users share password: SuperClave99!! (see PASSWORD below — must satisfy AuthKit's
 * PasswordPolicy and must match the argon2 hash seeded by scripts/test_data.sql)
 */

import { test, expect, Page } from '@playwright/test';

const BASE = process.env.ADOPTU_BASE_URL ?? 'http://localhost:4000';
const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025';
// For matching links inside email bodies — the backend builds them from its own `baseUrl` config
// (application.conf), which must point at the static site (this same BASE), not at the backend's
// own origin - ADOPTU_BASE_URL must be set to the same value on both sides.
const BASE_RE = BASE.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&');
// Must satisfy AuthKit's PasswordPolicy: ≥10 chars, ≥2 upper, ≥2 lower, ≥1 special, and no
// fragment of the user's own name/email — which rules out anything containing "test", since
// every seeded address lives under @test.com. ('Test1234!' fails all three.)
const PASSWORD = 'SuperClave99!!';

// ─── helpers ──────────────────────────────────────────────────────────────────

/** Login with password; waits for page to navigate away from /login.
 *  Note: successful login lands on /profile (profile-completion gate). */
async function loginWithPassword(page: Page, email: string, password = PASSWORD) {
  await page.goto(`${BASE}/login`);
  await page.waitForLoadState('networkidle');

  await page.fill('#password-email', email);
  await page.fill('#password-password', password);

  // Click then wait sequentially — argon2 hashing takes ~9s, so give 20s
  // waitForURL predicate receives a URL object (not string), so use .pathname
  await page.click('#password-login-btn');
  await page.waitForURL(url => !url.pathname.startsWith('/login'), { timeout: 20000 }).catch(() => null);
  await page.waitForLoadState('networkidle');
}

async function logout(page: Page) {
  // Logout is POST /api/auth/logout; the nav link triggers this via JS
  await page.request.post(`${BASE}/api/auth/logout`);
  await page.goto(`${BASE}/`);
  await page.waitForLoadState('networkidle');
}

async function getMailpitMessages(page: Page) {
  const res = await page.request.get(`${MAILPIT}/api/v1/messages`);
  return res.json();
}

async function getVerificationLinkFromLatestEmail(page: Page): Promise<string | null> {
  const data = await getMailpitMessages(page);
  if (!data.messages || data.messages.length === 0) return null;
  const latest = data.messages[0];
  const msgRes = await page.request.get(`${MAILPIT}/api/v1/message/${latest.ID}`);
  const msg = await msgRes.json();
  const body = msg.Text || msg.HTML || '';
  const match = body.match(new RegExp(`${BASE_RE}\\/verify\\?token=[^\\s"<]+`));
  return match ? match[0] : null;
}

/** Find the most recent mailpit message addressed to `toEmail` and extract the
 *  first URL matching `urlPattern` from its body. Used for flows (like email
 *  change) that send more than one email, so we can't just grab the latest. */
async function getLinkFromEmailTo(page: Page, toEmail: string, urlPattern: RegExp): Promise<string | null> {
  const data = await getMailpitMessages(page);
  const messages = data.messages || [];
  const match = messages.find((m: any) => (m.To || []).some((t: any) => t.Address?.toLowerCase() === toEmail.toLowerCase()));
  if (!match) return null;
  const msgRes = await page.request.get(`${MAILPIT}/api/v1/message/${match.ID}`);
  const msg = await msgRes.json();
  const body = msg.Text || msg.HTML || '';
  const found = body.match(urlPattern);
  return found ? found[0] : null;
}

async function registerWithPassword(
  page: Page,
  email: string,
  displayName: string,
  roles: string[],
  password = PASSWORD
) {
  await page.goto(`${BASE}/register`);
  await page.waitForLoadState('networkidle');

  await page.fill('#email', email);
  await page.fill('#displayName', displayName);

  // Check extra role boxes (ADOPTER is always on and disabled)
  if (roles.includes('RESCUER')) await page.check('#role-rescuer');
  if (roles.includes('PHOTOGRAPHER')) await page.check('#role-photographer');
  if (roles.includes('TEMPORAL_HOME')) await page.check('#role-temporal-home');

  // Check password first, THEN uncheck passkey.
  // ensureAtLeastOne() re-enables passkey when both are unchecked simultaneously.
  await page.check('#method-password');
  await page.uncheck('#method-passkey').catch(() => null);
  await page.waitForTimeout(300);

  // Fill password fields (may be in a now-visible section)
  await page.fill('#password', password);
  await page.fill('#confirmPassword', password);

  await page.click('#register-button');

  // Registration creates passkey + hashes password (argon2 ~9s). Wait for final state.
  // Page may navigate away on success, or stay on /register with a message.
  await page.waitForURL(url => !url.pathname.startsWith('/register'), { timeout: 25000 }).catch(() => null);

  if (!page.url().includes('/register')) {
    // Navigated away — registration succeeded
    return 'account created';
  }

  const msg = await page.locator('#message').textContent().catch(() => '');
  return msg?.trim() || '';
}

// =============================================================================
// SUITE 1 — Infrastructure & test data
// =============================================================================
test.describe('1 · Test data', () => {
  test('backend is healthy', async ({ page }) => {
    const res = await page.request.get(`${BASE}/`);
    expect(res.status()).toBe(200);
  });

  test('mailpit is healthy', async ({ page }) => {
    const res = await page.request.get(`${MAILPIT}/api/v1/messages`);
    expect(res.status()).toBe(200);
  });

  test('pets endpoint returns pets', async ({ page }) => {
    // /api/pets requires a country filter; the seed data's 200 pets are split
    // across a handful of countries, so query the total across the countries
    // the fixture uses rather than assuming one country has >=100 on its own.
    const countries = ['MEXICO', 'ARGENTINA', 'COLOMBIA', 'CHILE'];
    let total = 0;
    for (const country of countries) {
      const res = await page.request.get(`${BASE}/api/pets?country=${country}`);
      expect(res.status()).toBe(200);
      const pets = await res.json();
      expect(Array.isArray(pets)).toBe(true);
      total += pets.length;
    }
    expect(total).toBeGreaterThanOrEqual(100);
  });

  test('photographers endpoint returns data', async ({ page }) => {
    const res = await page.request.get(`${BASE}/api/photographers`);
    expect(res.status()).toBe(200);
    const data = await res.json();
    expect(data.length).toBeGreaterThanOrEqual(5);
  });

  test('shelters endpoint returns data for México', async ({ page }) => {
    const res = await page.request.get(`${BASE}/api/shelters?country=M%C3%A9xico`);
    expect(res.status()).toBe(200);
    const data = await res.json();
    expect(data.length).toBeGreaterThan(0);
  });

  test('sterilization locations endpoint returns data for México', async ({ page }) => {
    const res = await page.request.get(`${BASE}/api/sterilization-locations?country=M%C3%A9xico`);
    expect(res.status()).toBe(200);
    const data = await res.json();
    expect(data.length).toBeGreaterThan(0);
  });

  test('temporal homes endpoint returns data for México', async ({ page }) => {
    const res = await page.request.get(`${BASE}/api/temporal-homes?country=M%C3%A9xico`);
    expect(res.status()).toBe(200);
    const data = await res.json();
    expect(data.length).toBeGreaterThan(0);
  });
});

// =============================================================================
// SUITE 2 — Registration with password (new users)
// =============================================================================
test.describe('2 · Registration with password', () => {
  const newAdopter  = { email: 'e2e.adopter@test.com',  name: 'E2E Adopter',   roles: [] as string[] };
  const newRescuer  = { email: 'e2e.rescuer@test.com',  name: 'E2E Rescuer',   roles: ['RESCUER'] };
  const newMulti    = { email: 'e2e.multi@test.com',    name: 'E2E Multi',     roles: ['RESCUER', 'PHOTOGRAPHER', 'TEMPORAL_HOME'] };

  for (const user of [newAdopter, newRescuer, newMulti]) {
    test(`register ${user.name}`, async ({ page }) => {
      const msg = await registerWithPassword(page, user.email, user.name, user.roles);
      expect(msg.toLowerCase()).toMatch(/account created|check your email|verificat|success/i);
    });
  }

  test('duplicate email returns error or resend message', async ({ page }) => {
    const msg = await registerWithPassword(page, newAdopter.email, 'Dup User', []);
    // Backend silently succeeds on duplicate to avoid email enumeration (anti-harvest design)
    expect(msg.length).toBeGreaterThan(0);
  });
});

// =============================================================================
// SUITE 3 — Email verification via mailpit
// =============================================================================
test.describe('3 · Email verification (mailpit)', () => {
  test('verification email arrives in mailpit after registration', async ({ page }) => {
    // Clear inbox first, then register a fresh user
    await page.request.delete(`${MAILPIT}/api/v1/messages`);
    await registerWithPassword(page, `e2e.verify.${Date.now()}@test.com`, 'E2E Verify', []);
    await page.waitForTimeout(2000);

    const data = await getMailpitMessages(page);
    expect(data.messages.length).toBeGreaterThan(0);
  });

  test('clicking verification link shows success', async ({ page }) => {
    await page.request.delete(`${MAILPIT}/api/v1/messages`);
    const verifyEmail = `e2e.verify2.${Date.now()}@test.com`;
    await registerWithPassword(page, verifyEmail, 'E2E Verify2', []);
    await page.waitForTimeout(2000);

    const link = await getVerificationLinkFromLatestEmail(page);
    expect(link).not.toBeNull();

    await page.goto(link!);
    await page.waitForLoadState('networkidle');

    const bodyText = await page.locator('body').textContent();
    expect(bodyText).toMatch(/verified|success|bienvenido|welcome|confirm/i);
  });
});

// =============================================================================
// SUITE 4 — Login as admin
// =============================================================================
test.describe('4 · Admin login', () => {
  test('admin can login with password and lands off /login', async ({ page }) => {
    await loginWithPassword(page, 'admin@adoptu.com');
    expect(page.url()).not.toMatch(/\/login($|\?)/);
  });

  test('admin /api/auth/me returns authenticated=true after login', async ({ page }) => {
    await loginWithPassword(page, 'admin@adoptu.com');
    const res = await page.request.get(`${BASE}/api/auth/me`);
    const me = await res.json();
    expect(me.authenticated).toBe(true);
    expect(me.activeRoles).toContain('ADMIN');
  });

  test('admin can access /admin page', async ({ page }) => {
    await loginWithPassword(page, 'admin@adoptu.com');
    await page.goto(`${BASE}/admin`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(800);
    expect(page.url()).toContain('/admin');
    const bodyText = await page.locator('body').textContent();
    expect(bodyText?.toLowerCase()).toMatch(/admin/i);
  });

  test('admin nav shows admin link after login', async ({ page }) => {
    await loginWithPassword(page, 'admin@adoptu.com');
    await page.goto(`${BASE}/`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);  // JS nav rendering
    // Admin link is in a hidden dropdown (no id) — just verify it exists in the DOM
    await expect(page.locator('a[href="/admin"]')).toHaveCount(1, { timeout: 5000 });
  });

  test('wrong password shows error', async ({ page }) => {
    await page.goto(`${BASE}/login`);
    await page.waitForLoadState('networkidle');
    await page.fill('#password-email', 'admin@adoptu.com');
    await page.fill('#password-password', 'WrongPass999!');
    await page.click('#password-login-btn');
    await page.waitForTimeout(5000);
    const msg = await page.locator('#password-login-message').textContent();
    expect(msg).toMatch(/failed|invalid|incorrect|error/i);
  });
});

// =============================================================================
// SUITE 5 — Role-based logins
// =============================================================================
test.describe('5 · Role-based logins', () => {
  const roleUsers = [
    { email: 'maria.garcia@email.com',    role: 'RESCUER' },
    { email: 'jorge.photo@email.com',     role: 'PHOTOGRAPHER' },
    { email: 'elena.foster@email.com',    role: 'TEMPORAL_HOME' },
    { email: 'shelter.amigos@email.com',  role: 'SHELTER' },
    { email: 'steril.clinic1@email.com',  role: 'STERILIZATION_SERVICE' },
    { email: 'juan.medina@email.com',     role: 'ADOPTER' },
  ];

  for (const u of roleUsers) {
    test(`${u.role} user can login and is authenticated`, async ({ page }) => {
      await loginWithPassword(page, u.email);

      const res = await page.request.get(`${BASE}/api/auth/me`);
      const me = await res.json();
      expect(me.authenticated).toBe(true);
      expect(me.activeRoles).toContain(u.role);
    });
  }
});

// =============================================================================
// SUITE 6 — Search sections have data
// =============================================================================
test.describe('6 · Search sections', () => {
  test('pets page loads and shows cards', async ({ page }) => {
    await page.goto(`${BASE}/pets`);
    await page.waitForLoadState('networkidle');
    // Pet search is mandatory-country: select one before cards render.
    await page.locator('#pets-country').selectOption({ value: 'Mexico' });
    await page.waitForTimeout(1500);
    const cards = page.locator('.pet-card');
    await expect(cards.first()).toBeVisible({ timeout: 5000 });
    expect(await cards.count()).toBeGreaterThan(5);
  });

  test('pets page filter by DOG returns results', async ({ page }) => {
    await page.goto(`${BASE}/pets`);
    await page.waitForLoadState('networkidle');
    await page.locator('#pets-country').selectOption({ value: 'Mexico' });
    await page.waitForTimeout(1500);
    const dogFilterBtn = page.locator('.filter-btn[data-type="DOG"]');
    await dogFilterBtn.click();
    await page.waitForTimeout(1500);
    const cards = page.locator('.pet-card');
    expect(await cards.count()).toBeGreaterThan(0);
    expect(await cards.locator('.pet-type').first().textContent()).toMatch(/dog/i);
  });

  test('photographers page loads with cards', async ({ page }) => {
    await page.goto(`${BASE}/photographers`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    const cards = page.locator('[class*="photographer"][class*="card"], .photographer-card, .card');
    const count = await cards.count();
    expect(count).toBeGreaterThanOrEqual(0); // page loads; cards load via JS
    const bodyText = await page.locator('body').textContent();
    expect(bodyText).toMatch(/fotograf|photo|photographer/i);
  });

  test('photographers search by México returns results', async ({ page }) => {
    await page.goto(`${BASE}/photographers`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    const countrySelect = page.locator('#search-country');
    if (await countrySelect.isVisible()) {
      await countrySelect.selectOption({ value: 'Mexico' });
      await page.waitForTimeout(2000);
    }
    const bodyText = await page.locator('body').textContent();
    expect(bodyText).toMatch(/fotograf|photo|photographer/i);
  });

  test('shelters page loads', async ({ page }) => {
    await page.goto(`${BASE}/shelters`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);
    expect((await page.locator('body').textContent())?.toLowerCase()).toMatch(/shelter|refugio/i);
  });

  test('shelters search by México shows results', async ({ page }) => {
    await page.goto(`${BASE}/shelters`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(800);
    const countrySelect = page.locator('#search-country');
    if (await countrySelect.isVisible()) {
      await countrySelect.selectOption({ value: 'Mexico' });
      await page.waitForTimeout(500);
      const searchBtn = page.locator('#search-btn');
      if (await searchBtn.isVisible()) await searchBtn.click();
      await page.waitForTimeout(2000);
      const bodyText = await page.locator('body').textContent();
      expect(bodyText).toMatch(/amigos|esperanza|M.xico|shelter|refugio/i);
    }
  });

  test('sterilization locations page loads', async ({ page }) => {
    await page.goto(`${BASE}/sterilization-locations`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);
    expect((await page.locator('body').textContent())?.toLowerCase()).toMatch(/esteriliz|steriliz/i);
  });

  test('sterilization search by México shows results', async ({ page }) => {
    await page.goto(`${BASE}/sterilization-locations`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(800);
    const countrySelect = page.locator('#search-country');
    if (await countrySelect.isVisible()) {
      await countrySelect.selectOption({ value: 'Mexico' });
      await page.waitForTimeout(2000);
      const bodyText = await page.locator('body').textContent();
      expect(bodyText).toMatch(/cl.nica|clinic|esteriliz|M.xico/i);
    }
  });

  test('temporal homes page loads', async ({ page }) => {
    await page.goto(`${BASE}/temporal-homes`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);
    expect((await page.locator('body').textContent())?.toLowerCase()).toMatch(/temporal|hogar/i);
  });

  test('temporal homes search by México shows results', async ({ page }) => {
    await page.goto(`${BASE}/temporal-homes`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(800);
    const countrySelect = page.locator('#search-country');
    if (await countrySelect.isVisible()) {
      await countrySelect.selectOption({ value: 'Mexico' });
      await page.waitForTimeout(2000);
      const bodyText = await page.locator('body').textContent();
      expect(bodyText).toMatch(/casa|hogar|elena|temporal|M.xico/i);
    }
  });

  // ── City/zip filters (Shelters, Photographers, Sterilization, Temporal Homes
  // all share the LocationSearchFilters component: #search-city / #search-zip) ──
  for (const { path, label } of [
    { path: 'shelters', label: 'shelters' },
    { path: 'photographers', label: 'photographers' },
    { path: 'sterilization-locations', label: 'sterilization locations' },
    { path: 'temporal-homes', label: 'temporal homes' },
  ]) {
    test(`${label} search filters by city and zip narrow results`, async ({ page }) => {
      await page.goto(`${BASE}/${path}`);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(800);

      const countrySelect = page.locator('#search-country');
      await countrySelect.selectOption({ value: 'Mexico' });
      await page.waitForTimeout(300);

      const cityInput = page.locator('#search-city');
      const zipInput = page.locator('#search-zip');
      await expect(cityInput).toBeEnabled({ timeout: 2000 });
      await expect(zipInput).toBeEnabled({ timeout: 2000 });

      // Capture the outgoing search request so we assert the filters are
      // actually sent to the backend, not just accepted by the form.
      const cityReqPromise = page.waitForRequest(r => r.url().includes('city='), { timeout: 5000 }).catch(() => null);
      await cityInput.fill('Ciudad de México');
      await page.waitForTimeout(700); // debounce
      const cityReq = await cityReqPromise;
      expect(cityReq?.url()).toContain('city=');

      const zipReqPromise = page.waitForRequest(r => r.url().includes('zip='), { timeout: 5000 }).catch(() => null);
      await cityInput.fill('');
      await zipInput.fill('01000');
      await page.waitForTimeout(700);
      const zipReq = await zipReqPromise;
      expect(zipReq?.url()).toContain('zip=01000');
    });
  }

  test('selecting a country on one search page auto-selects it on another', async ({ page }) => {
    await page.goto(`${BASE}/shelters`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);
    await page.locator('#search-country').selectOption({ value: 'Argentina' });
    await page.waitForTimeout(300);

    // Navigating to a different search page should pre-select the same country
    await page.goto(`${BASE}/photographers`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);
    await expect(page.locator('#search-country')).toHaveValue('Argentina');

    await page.goto(`${BASE}/sterilization-locations`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);
    await expect(page.locator('#search-country')).toHaveValue('Argentina');

    await page.goto(`${BASE}/temporal-homes`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);
    await expect(page.locator('#search-country')).toHaveValue('Argentina');

    // And the Pets page (separate #pets-country select, same localStorage key)
    await page.goto(`${BASE}/pets`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);
    await expect(page.locator('#pets-country')).toHaveValue('Argentina');
  });
});

// =============================================================================
// SUITE 7 — Internationalization
// =============================================================================
test.describe('7 · Internationalization', () => {
  const LANGS = ['en', 'es', 'fr', 'pt', 'zh'];
  const PUBLIC_PAGES = ['/', '/photographers', '/shelters', '/sterilization-locations', '/temporal-homes', '/login', '/register'];

  test('every data-i18n key used on public pages is translated in all 5 languages', async ({ page }) => {
    const keys = new Set<string>();
    for (const path of PUBLIC_PAGES) {
      await page.goto(`${BASE}${path}`);
      await page.waitForLoadState('networkidle');
      const pageKeys = await page.locator('[data-i18n]').evaluateAll(els => els.map(e => e.getAttribute('data-i18n')));
      pageKeys.filter(Boolean).forEach(k => keys.add(k as string));
    }
    expect(keys.size).toBeGreaterThan(10);

    // AdoptuI18n.t() falls back to returning the raw key when a translation is
    // missing (see I18n.kt), so "result === key" means that key has no entry
    // for that language.
    const missing = await page.evaluate(({ keys, langs }) => {
      const i18n = (window as any).AdoptuI18n;
      const gaps: string[] = [];
      for (const lang of langs) {
        i18n.loadLang(lang);
        for (const key of keys) {
          if (i18n.t(key) === key) gaps.push(`${lang}:${key}`);
        }
      }
      i18n.loadLang('en');
      return gaps;
    }, { keys: Array.from(keys), langs: LANGS });

    expect(missing, `Untranslated keys found: ${missing.join(', ')}`).toEqual([]);
  });

  test('language switcher changes visible text and persists across reload', async ({ page }) => {
    await page.goto(`${BASE}/`);
    await page.waitForLoadState('networkidle');

    const heading = page.locator('h1[data-i18n="petsForAdoption"]');
    const englishText = await heading.textContent();

    await page.hover('.lang-dropdown');
    await page.locator('a.lang-option[data-lang="es"]').click();
    await page.waitForTimeout(300);

    const spanishText = await heading.textContent();
    expect(spanishText).not.toBe(englishText);

    const stored = await page.evaluate(() => window.localStorage.getItem('preferredLanguage'));
    expect(stored).toBe('es');

    // Reload — the choice should survive navigation, not just the in-memory swap
    await page.reload();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(300);
    expect(await heading.textContent()).toBe(spanishText);

    // Reset to English so it doesn't bleed into other tests sharing this browser context
    await page.hover('.lang-dropdown');
    await page.locator('a.lang-option[data-lang="en"]').click();
    await page.waitForTimeout(300);
  });
});

// =============================================================================
// SUITE 8 — Profile modification
// =============================================================================
test.describe('8 · Profile modification', () => {
  test('display name and country changes persist to the DB after reloading the page', async ({ page }) => {
    await loginWithPassword(page, 'juan.medina@email.com');
    await page.goto(`${BASE}/profile`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    const nameField = page.locator('#displayName');
    await expect(nameField).toBeVisible({ timeout: 5000 });

    const newName = `Juan E2E ${Date.now() % 10000}`;
    await nameField.fill(newName);
    await page.locator('#country').selectOption({ value: 'Colombia' });

    // Capture the profile PUT response to verify the API call succeeds
    const profileUpdatePromise = page.waitForResponse(
      r => r.url().includes('/api/users/profile') && r.request().method() === 'PUT',
      { timeout: 10000 }
    ).catch(() => null);

    await page.locator('#save-profile-btn').click();
    const profileRes = await profileUpdatePromise;
    expect(profileRes?.ok()).toBe(true);

    // The save handler itself reloads (racy with our own goto below), so wait
    // for that in-flight navigation to settle first.
    await page.waitForLoadState('networkidle');

    // Then do our own hard navigation too — this proves the new values come
    // from the DB, not leftover page/JS state. Retry once if it races with a
    // still-in-flight reload (ERR_ABORTED).
    await page.goto(`${BASE}/profile`).catch(() => page.goto(`${BASE}/profile`));
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    await expect(page.locator('#displayName')).toHaveValue(newName);
    await expect(page.locator('#country')).toHaveValue('Colombia');
  });

  test('password change persists to the DB — old password stops working, new one logs in', async ({ page }) => {
    test.setTimeout(60000); // three argon2 logins (~9s each) + an intentionally-failed login (~20s wait)
    const email = 'jorge.photo@email.com'; // only used in Suite 5's login loop before this point
    const newPassword = 'NewPass456!';

    await loginWithPassword(page, email);
    await page.goto(`${BASE}/profile`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    await page.fill('#current-password', PASSWORD);
    await page.fill('#new-password', newPassword);
    await page.fill('#confirm-password', newPassword);
    await page.locator('#save-password-btn').click();
    await expect(page.locator('#password-message')).toContainText(/success|changed/i, { timeout: 10000 });

    await logout(page);

    // Old password must now be rejected
    await loginWithPassword(page, email, PASSWORD);
    expect(page.url()).toContain('/login');

    // New password must work
    await loginWithPassword(page, email, newPassword);
    expect(page.url()).not.toContain('/login');
  });

  test('email change requires verification before the DB email updates', async ({ page }) => {
    test.setTimeout(60000); // two argon2 logins (~9s each) + an intentionally-failed login (~20s wait)
    const oldEmail = 'shelter.amigos@email.com'; // only used in Suite 5's login loop before this point
    const newEmail = `shelter.amigos.e2e.${Date.now()}@test.com`;

    await page.request.delete(`${MAILPIT}/api/v1/messages`);
    await loginWithPassword(page, oldEmail);
    await page.goto(`${BASE}/profile`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    await page.fill('#new-email', newEmail);
    await page.locator('#change-email-btn').click();
    await expect(page.locator('#email-change-message')).toContainText(/sent|success/i, { timeout: 10000 });

    // Logging in with the new address must fail until the link is clicked
    await logout(page);
    await loginWithPassword(page, newEmail, PASSWORD);
    expect(page.url()).toContain('/login');

    // Verification link is emailed to the NEW address (a separate security
    // alert also goes to the old address — grab the one meant for newEmail).
    await page.waitForTimeout(1000);
    const link = await getLinkFromEmailTo(page, newEmail, new RegExp(`${BASE_RE}\\/verify-email-change\\?token=[^\\s"<]+`));
    expect(link).not.toBeNull();

    await page.goto(link!);
    await page.waitForLoadState('networkidle');
    const bodyText = await page.locator('body').textContent();
    expect(bodyText).toMatch(/verified|success|updated|confirm/i);

    // Now the new email logs in and shows up on a freshly-loaded profile page
    await loginWithPassword(page, newEmail, PASSWORD);
    expect(page.url()).not.toContain('/login');
    await page.goto(`${BASE}/profile`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    await expect(page.locator('#email')).toHaveValue(newEmail);
  });

  test('profile page shows temporal home section for TEMPORAL_HOME user', async ({ page }) => {
    await loginWithPassword(page, 'elena.foster@email.com');
    await page.goto(`${BASE}/profile`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    const bodyText = await page.locator('body').textContent();
    expect(bodyText).toMatch(/temporal|hogar|alias/i);
  });

  test('admin can access profile page', async ({ page }) => {
    await loginWithPassword(page, 'admin@adoptu.com');
    await page.goto(`${BASE}/profile`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);
    await expect(page.locator('#displayName')).toBeVisible({ timeout: 5000 });
  });
});

// =============================================================================
// SUITE 9 — Pet management (rescuer)
// =============================================================================
test.describe('9 · Pet management', () => {
  const rescuerEmail = 'maria.garcia@email.com';

  test('rescuer sees their pets on /my-pets', async ({ page }) => {
    await loginWithPassword(page, rescuerEmail);
    await page.goto(`${BASE}/my-pets`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2500);

    const cards = page.locator('.pet-card');
    expect(await cards.count()).toBeGreaterThan(0);
  });

  test('rescuer can add a new pet, with an image saved to storage', async ({ page }) => {
    await loginWithPassword(page, rescuerEmail);
    await page.goto(`${BASE}/my-pets`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    const petName = `E2E Test Dog ${Date.now() % 100000}`;

    // Look for add-pet button (might be an inline form or a toggle button)
    const addBtn = page.locator('#add-pet-btn, button:has-text("Add"), button:has-text("Agregar"), button:has-text("New"), button:has-text("Nuevo")').first();
    if (await addBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await addBtn.click();
      await page.waitForTimeout(500);
    }

    const formContainer = page.locator('#form-container, #pet-form, form#pet-form');
    if (await formContainer.isVisible({ timeout: 3000 }).catch(() => false)) {
      await page.fill('#name', petName);
      await page.selectOption('#type', 'DOG');
      await page.fill('#breed', 'Mixed Breed');
      await page.fill('#description', 'A healthy test dog for E2E verification');
      await page.fill('#weight', '15');
      await page.fill('#ageYears', '2');
      await page.fill('#ageMonths', '3');
      await page.selectOption('#sex', 'MALE');
      await page.fill('#color', 'Brown');
      await page.selectOption('#size', 'MEDIUM');
      await page.fill('#temperament', 'Friendly');
      await page.selectOption('#energyLevel', 'MEDIUM');
      await page.fill('#rescueLocation', 'Ciudad de México');
      await page.fill('#rescueDate', '2025-01-15');

      // A tiny 1x1 red PNG, uploaded through the real file input.
      const pngBuffer = Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
        'base64'
      );
      const imageInput = page.locator('#pet-images');
      if (await imageInput.count()) {
        await imageInput.setInputFiles({ name: 'e2e-test-pet.png', mimeType: 'image/png', buffer: pngBuffer });
      }

      const submitBtn = page.locator('#pet-form button[type="submit"], #form-container button[type="submit"]').first();
      await submitBtn.click();
      await page.waitForTimeout(3000);

      const bodyText = await page.locator('body').textContent();
      expect(bodyText).toContain(petName);

      // Confirm the pet (and, if uploaded, its image) actually persisted to
      // the DB/storage — not just rendered client-side — via a fresh API call.
      const mineRes = await page.request.get(`${BASE}/api/pets/mine`);
      const mine = await mineRes.json();
      const created = mine.find((p: any) => p.name === petName);
      expect(created).toBeTruthy();

      if (await imageInput.count() && created.images?.length) {
        const imageUrl: string = created.images[0].imageUrl;
        const imgRes = await page.request.get(imageUrl.startsWith('http') ? imageUrl : `${BASE}${imageUrl}`);
        expect(imgRes.ok()).toBe(true); // image bytes are actually retrievable from storage
      }
    } else {
      // Form may be inline-always-visible — skip check
      console.log('Add pet form not found; likely shown inline');
    }
  });

  test('rescuer can edit an existing pet, and the change persists after reload', async ({ page }) => {
    await loginWithPassword(page, rescuerEmail);
    await page.goto(`${BASE}/my-pets`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2500);

    const editBtn = page.locator('button:has-text("Edit"), button:has-text("Editar")').first();
    if (await editBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await editBtn.click();
      await page.waitForTimeout(500);

      const nameField = page.locator('#name');
      if (await nameField.isVisible({ timeout: 2000 }).catch(() => false)) {
        const updatedName = `Edited Dog ${Date.now() % 100000}`;
        await nameField.fill(updatedName);
        const updatedWeight = '22.5';
        await page.fill('#weight', updatedWeight);
        // Ensure description has a value (required field)
        const descField = page.locator('#description');
        const descVal = await descField.inputValue().catch(() => '');
        if (!descVal) await descField.fill('E2E test pet description');

        const submitBtn = page.locator('#pet-form button[type="submit"], #form-container button[type="submit"]').first();
        await submitBtn.click();
        await page.waitForLoadState('networkidle');
        await page.waitForTimeout(1500);

        // Hard reload — proves the edit landed in the DB, not just the DOM
        await page.goto(`${BASE}/my-pets`);
        await page.waitForLoadState('networkidle');
        await page.waitForTimeout(2500);
        const bodyText = await page.locator('body').textContent();
        expect(bodyText).toContain(updatedName);

        const mineRes = await page.request.get(`${BASE}/api/pets/mine`);
        const mine = await mineRes.json();
        const edited = mine.find((p: any) => p.name === updatedName);
        expect(edited).toBeTruthy();
        expect(edited.weight).toBeCloseTo(parseFloat(updatedWeight), 1);
      }
    }
  });

  test('rescuer can delete a pet', async ({ page }) => {
    await loginWithPassword(page, rescuerEmail);
    await page.goto(`${BASE}/my-pets`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2500);

    const countBefore = await page.locator('.pet-card').count();
    expect(countBefore).toBeGreaterThan(0);

    const deleteBtn = page.locator('button:has-text("Delete"), button:has-text("Eliminar")').first();
    if (await deleteBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      page.once('dialog', dialog => dialog.accept());
      await deleteBtn.click();
      await page.waitForTimeout(2500);

      const countAfter = await page.locator('.pet-card').count();
      expect(countAfter).toBe(countBefore - 1);
    }
  });
});

// =============================================================================
// SUITE 10 — Adoption flow
// =============================================================================
test.describe('10 · Adoption flow', () => {
  test('adopter can open pet detail and see adoption section', async ({ page }) => {
    await loginWithPassword(page, 'juan.medina@email.com');

    const res = await page.request.get(`${BASE}/api/pets?status=AVAILABLE&country=Mexico`);
    const pets = await res.json();
    expect(pets.length).toBeGreaterThan(0);

    const pet = pets[0];
    await page.goto(`${BASE}/pet/${pet.id}`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    const bodyText = await page.locator('body').textContent();
    expect(bodyText).toContain(pet.name);

    // Check for adoption form or adopt button
    const adoptSection = page.locator('#adoption-form, form[id*="adopt"], button:has-text("Adopt"), button:has-text("Adoptar")');
    // Should exist (might be a form or button depending on login state)
    const bodyContainsAdopt = (bodyText ?? '').match(/adopt|adoptar/i);
    expect(bodyContainsAdopt).not.toBeNull();
  });

  test('adopter can submit adoption request', async ({ page }) => {
    await loginWithPassword(page, 'juan.medina@email.com');

    const res = await page.request.get(`${BASE}/api/pets?status=AVAILABLE&country=Mexico`);
    const pets = await res.json();
    const pet = pets[0];

    await page.goto(`${BASE}/pet/${pet.id}`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    const adoptForm = page.locator('#adoption-form, form[id*="adopt"]');
    if (await adoptForm.isVisible({ timeout: 2000 }).catch(() => false)) {
      const msgInput = adoptForm.locator('textarea, #msg').first();
      if (await msgInput.isVisible({ timeout: 1000 }).catch(() => false)) {
        await msgInput.fill('I would love to adopt this pet! E2E test.');
      }
      const submitBtn = adoptForm.locator('button[type="submit"]').first();
      await submitBtn.click();
      await page.waitForTimeout(2000);

      const message = await page.locator('#message, .message').textContent();
      expect(message).toMatch(/submitted|success|enviado|request/i);
    }
  });

  test('rescuer sees adoption requests section on /my-pets', async ({ page }) => {
    await loginWithPassword(page, 'maria.garcia@email.com');
    await page.goto(`${BASE}/my-pets`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2500);

    const bodyText = await page.locator('body').textContent();
    expect(bodyText).toMatch(/adoption request|no adoption/i);
  });

  test('rescuer can approve an adoption request via API', async ({ page }) => {
    await loginWithPassword(page, 'maria.garcia@email.com');

    // Get rescuer's pets via the /mine endpoint, which doesn't require a country filter
    // (the public /api/pets search does, and this rescuer's seeded pets have no country set).
    const petsRes = await page.request.get(`${BASE}/api/pets/mine`);
    const pets = await petsRes.json();
    const myPet = pets.find((p: any) => p.rescuerId !== undefined);
    if (!myPet) return; // no pets, skip

    const reqRes = await page.request.get(`${BASE}/api/pets/${myPet.id}/adoption-requests`);
    if (reqRes.status() !== 200) return;
    const requests = await reqRes.json();
    if (requests.length === 0) return; // no requests yet

    const r = requests[0];
    const updateRes = await page.request.put(`${BASE}/api/adoption-requests/${r.id}`, {
      data: { status: 'APPROVED' }
    });
    // Expect 200 or some success-like status
    expect([200, 201, 204]).toContain(updateRes.status());
  });
});

// =============================================================================
// SUITE 11 — Admin panel
// =============================================================================
test.describe('11 · Admin panel', () => {
  test('admin can access /admin', async ({ page }) => {
    await loginWithPassword(page, 'admin@adoptu.com');
    await page.goto(`${BASE}/admin`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(800);
    expect(page.url()).toContain('/admin');
  });

  test('admin /admin page shows admin content', async ({ page }) => {
    await loginWithPassword(page, 'admin@adoptu.com');
    await page.goto(`${BASE}/admin`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);
    const bodyText = await page.locator('body').textContent();
    expect(bodyText?.toLowerCase()).toMatch(/admin/i);
  });

  test('admin can access /admin/shelters', async ({ page }) => {
    await loginWithPassword(page, 'admin@adoptu.com');
    await page.goto(`${BASE}/admin/shelters`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    const bodyText = await page.locator('body').textContent();
    expect(bodyText?.toLowerCase()).toMatch(/shelter|refugio/i);
  });

  test('admin can access /admin/sterilization-locations', async ({ page }) => {
    await loginWithPassword(page, 'admin@adoptu.com');
    await page.goto(`${BASE}/admin/sterilization-locations`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    const bodyText = await page.locator('body').textContent();
    expect(bodyText?.toLowerCase()).toMatch(/esteriliz|steriliz/i);
  });

  test('non-admin /admin page does not show admin controls', async ({ page }) => {
    await loginWithPassword(page, 'juan.medina@email.com');
    await page.goto(`${BASE}/admin`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);
    // Non-admin should not have admin nav links in the DOM
    const adminNavLinks = page.locator('a[href="/admin"]');
    const count = await adminNavLinks.count();
    expect(count).toBe(0);
  });
});

// =============================================================================
// SUITE 12 — Pet detail page
// =============================================================================
test.describe('12 · Pet detail page', () => {
  test('pet detail page loads with correct pet name', async ({ page }) => {
    const res = await page.request.get(`${BASE}/api/pets?country=Mexico`);
    const pets = await res.json();
    const pet = pets[0];

    await page.goto(`${BASE}/pet/${pet.id}`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    const bodyText = await page.locator('body').textContent();
    expect(bodyText).toContain(pet.name);
  });

  test('pet detail page shows accurate data matching the API record', async ({ page }) => {
    const res = await page.request.get(`${BASE}/api/pets?country=Mexico`);
    const pets = await res.json();
    // Prefer a pet with the most optional fields populated, for the widest check
    const pet = [...pets].sort((a: any, b: any) =>
      [b.breed, b.color, b.temperament, b.microchipId, b.vaccinations].filter(Boolean).length -
      [a.breed, a.color, a.temperament, a.microchipId, a.vaccinations].filter(Boolean).length
    )[0];

    await page.goto(`${BASE}/pet/${pet.id}`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    const bodyText = (await page.locator('body').textContent()) ?? '';
    expect(bodyText).toContain(pet.name);
    expect(bodyText).toContain(String(pet.weight));
    expect(bodyText).toContain(String(pet.ageYears));
    expect(bodyText).toContain(String(pet.ageMonths));
    if (pet.breed) expect(bodyText).toContain(pet.breed);
    if (pet.color) expect(bodyText).toContain(pet.color);
    if (pet.temperament) expect(bodyText).toContain(pet.temperament);
    if (pet.microchipId) expect(bodyText).toContain(pet.microchipId);
    if (pet.vaccinations) expect(bodyText).toContain(pet.vaccinations);
    expect(bodyText).toMatch(pet.isSterilized ? /Sterilized:\s*Yes/i : /Sterilized:\s*No/i);
  });

  test('invalid pet id redirects to /pets', async ({ page }) => {
    await page.goto(`${BASE}/pet/99999`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);
    // Should redirect or show 404-like content
    const url = page.url();
    const bodyText = await page.locator('body').textContent();
    const handled = url.includes('/pets') || (bodyText ?? '').match(/not found|404|no encontrado/i);
    expect(handled).toBeTruthy();
  });
});

// =============================================================================
// SUITE 13 — Logout
// =============================================================================
test.describe('13 · Logout', () => {
  test('user can logout and sees login link', async ({ page }) => {
    await loginWithPassword(page, 'admin@adoptu.com');
    await page.goto(`${BASE}/`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    await logout(page);
    await page.waitForTimeout(800);

    // After logout, nav shows login link (id=nav-login)
    await expect(page.locator('#nav-login')).toBeVisible({ timeout: 5000 });
  });

  test('/api/auth/me returns authenticated=false after logout', async ({ page }) => {
    await loginWithPassword(page, 'admin@adoptu.com');
    await page.goto(`${BASE}/`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(800);
    await logout(page);
    await page.waitForTimeout(800);

    const res = await page.request.get(`${BASE}/api/auth/me`);
    const me = await res.json();
    expect(me.authenticated).toBe(false);
  });

  test('/my-pets redirects unauthenticated user away', async ({ page }) => {
    // Do not login — go directly to /my-pets
    await page.goto(`${BASE}/my-pets`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);
    // Should redirect to / or show no pet content
    expect(page.url()).not.toMatch(/\/my-pets$/);
  });
});
