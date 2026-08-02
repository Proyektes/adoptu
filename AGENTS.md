# Adopt-U - Agent Guidelines

## Project

Kotlin Multiplatform pet adoption platform. Ktor backend + Kotlin/JS admin frontend. Server-side rendered HTML via kotlinx.html.

## Conventions

- **MPA, not SPA**: This is a multi-page application. Pages are full HTML responses, not API + client-side routing.
- **Kotlin-first**: Prefer Kotlin for everything — backend logic, frontend JS (via Kotlin/JS), and HTML generation (kotlinx.html). Avoid introducing Node.js, npm packages, or JS frameworks.
- **Clean Architecture**: Follow the ports/adapters pattern. Business logic lives in `services/`, interfaces in `ports/`, implementations in `adapters/`. Routes call services, services call ports.
- **Docs stay current**: When making important changes, update both `README.md` and `AGENTS.md` to reflect the new state.
- **Tests required**: New code must include tests. When removing code, review and remove its associated tests if no longer needed. Preferred ITs with business value. At least 90% coverage and 85 mutation test score. 

## Commands

```bash
./gradlew run              # Start dev server on :8080
./gradlew :backend:test    # Unit tests
./gradlew dockerUp         # Start Postgres + LocalStack + Mailpit for integration tests
./gradlew integrationTest  # Runs .*IT tests (requires dockerUp first)
./gradlew e2eTest          # Runs E2E tests with Playwright in Docker
./gradlew dockerDown       # Stop test containers
./gradlew shadowJar        # Build fat jar
./gradlew :backend:compileSass  # Compile SCSS → CSS (needs the Dart Sass CLI, `sass`, on PATH)
```

Run a single test: `./gradlew :backend:test --tests com.adoptu.SomeTest`

## Architecture

Multi-project build with `backend` and `frontend` subprojects.

**Entry point**: `backend/src/main/kotlin/com/adoptu/Application.kt` → `ApplicationKt.module`
**Config**: `backend/src/main/resources/application.conf` (HOCON, env overrides via `ADOPTU_ENV`)

### Layer structure (`src/main/kotlin/com/adoptu/`)

| Directory | Purpose |
|---|---|
| `plugins/` | Ktor plugins (Routing, Sessions, Serialization, WebAuthn, Responses) |
| `routes/` | Route handlers grouped by domain |
| `services/` | Business logic, validation subpackage |
| `ports/` | Repository/storage/notification interfaces |
| `adapters/db/repositories/` | Exposed JDBC implementations of ports |
| `adapters/storage/` | S3 image storage adapter |
| `adapters/notification/` | SES email adapter |
| `pages/` | kotlinx.html page renderers |
| `di/` | Koin dependency injection (`AppModule.kt`) |
| `dto/input/` | Request DTOs |
| `dto/output/` | Response DTOs |

### Environment selection

`ADOPTU_ENV` defaults to `"prod"`. Config sections are keyed by `db.dev` / `db.prod`, `storage.dev` / `storage.prod`. The env value selects which section is used.

### Database

- Uses Exposed ORM with PostgreSQL
- `DatabaseFactory.init()` auto-creates tables and columns on startup
- Dev defaults: `jdbc:postgresql://localhost:5432/adoptu`, user `adoptu`, password `Ad0ptU`
- Creates a default admin user if none exists

### Integration tests

Require Docker. `docker-compose.test.yml` starts:
- PostgreSQL 15 on port **15432** (user: `testuser`, pass: `testpassword`, db: `testdb`)
- LocalStack on port **4567** (S3 only)
- Mailpit on port **1025** (SMTP) and **8025** (web UI at http://localhost:8025)

E2E tests use Playwright in Docker (`./gradlew e2eTest`). Tests run inside `mcr.microsoft.com/playwright/java` container with `--network host` so the embedded Ktor server is accessible. E2E tests are excluded from the regular `test` task.

### Email (dev)

Dev mode uses SMTP via Mailpit. Run `./gradlew dockerUp` to start Mailpit, then view sent emails at http://localhost:8025. Default SMTP config: `localhost:1025` (no auth, no TLS). Override with `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_STARTTLS` env vars.

## Build quirks

- **Multi-project build**: `backend` (Ktor server) and `frontend` (Kotlin/JS) subprojects.
- **Custom `run` task**: `./gradlew run` is a custom `JavaExec` task (not from the `application` plugin, which conflicts with KMP).
- **SCSS → CSS flow**: source lives in `backend/src/main/scss/*.scss` (real hand-authored, using `@use` partials -- see `_variables.scss`, `_base.scss`, `_layout.scss`, `_location-search-form.scss`, `_admin.scss`). `:backend:compileSass` (an `Exec` task shelling out to the standalone Dart Sass CLI, `sass` on PATH -- no npm/Node) compiles it to `build/generated/scss/main/static/css/`, registered as an extra `main` resources source dir so `processResources` (and therefore `run`/`shadowJar`) picks it up automatically -- `src/main/resources/static/css/*.css` no longer exists; that's generated now, not committed.
- **Source dirs**: JVM sources are at `src/main/kotlin/` and `src/test/kotlin/` (non-standard for KMP, configured explicitly via `setSrcDirs`).
- `gradle.properties` has extensive `--add-opens` JVM args required for Kotlin 2.3+ and ByteBuddy
- Uses Amazon Corretto 25. Dockerfile installs Dart Sass 1.77.8 for SCSS compilation.
- `kotlin-js-store/` is auto-generated by Kotlin/JS (yarn.lock), safe to delete, recreated on build.
- AWS SDK `platform()` BOM is deprecated in Kotlin 2.3 — use explicit versioned dependencies instead.
- **E2E tests excluded from regular test run**: E2E tests in `com.adoptu.e2e.*` are excluded from `./gradlew :backend:test` and must be run via `./gradlew e2eTest` (requires Docker).

## Deploy

- AWS CodePipeline via `buildspec.yml`
- ECR repo: `174000857825.dkr.ecr.us-east-1.amazonaws.com/production/adoptu`
- Secrets from AWS Secrets Manager (DB URL, S3, SMTP)
- `start-local.sh` restarts Docker + starts LocalStack with a hardcoded auth token
