# Adopt-U - Pet Adoption Platform

A Kotlin pet adoption web application with **FIDO2/WebAuthn** passwordless authentication. Multi-page application: pages are static HTML generated at build time from Kotlin (kotlinx.html), served independently of the backend, which is a JSON API only.

## Features

- **FIDO2 Authentication**: Register and sign in with passkeys (biometrics, security keys)
- **Password + Magic Link login**: Alternative auth methods
- **Pet Types**: Dogs, cats, birds, fish
- **User Roles**:
  - **Admin**: Full system access
  - **Rescuers**: Publish and manage pet pages
  - **Adopters**: Browse pets and request adoptions
  - **Photographers**, **Shelters**, **Temporal Homes**, **Sterilization Services**

## Tech Stack

- **Backend**: Kotlin, Helidon, Exposed ORM - JSON API only, no page rendering
- **Database**: PostgreSQL
- **Auth**: WebAuthn4J (FIDO2/Passkeys)
- **Frontend**: static site generated at build time (Kotlin HTML / kotlinx.html DSL) + a Kotlin/JS
  client bundle for interactivity, deployed to CloudFront/S3 independently of the backend
- **Storage**: AWS S3 (LocalStack for dev)
- **Email**: AWS SES (Mailpit for dev)

## Requirements

- Java 17+
- Docker (for dev services)
- Modern browser with WebAuthn support (Chrome, Firefox, Edge, Safari)

## Run

Two processes: the backend serves the JSON API only (it no longer serves any HTML or static
assets); `:frontend:serveSite` generates the static site and serves it, proxying `/api/*` to the
backend.

```bash
./gradlew run                  # backend JSON API on http://localhost:8080
./gradlew :frontend:serveSite  # generates the static site, serves it on http://localhost:4000
```

Then open http://localhost:4000

**Note**: WebAuthn requires HTTPS or localhost. For production, use HTTPS.

## Development Services

Start Postgres, LocalStack (S3), and Mailpit (email):

```bash
./gradlew dockerUp
```

- **Database**: PostgreSQL on `localhost:5432`
- **S3**: LocalStack on `localhost:4566`
- **Email**: Mailpit web UI at http://localhost:8025

Stop services:

```bash
./gradlew dockerDown
```

## Project Structure

Three subprojects: `backend` (JSON API only), `frontend` (static site generator + browser JS
bundle), `common` (shared JVM/JS code).

```
backend/src/main/kotlin/com/adoptu/
├── Application.kt
├── adapters/          # DB repositories, S3 storage, SES email
├── di/                # Koin dependency injection
├── dto/               # Request/response DTOs
├── routes/            # Route handlers (JSON API only - no page rendering)
├── services/          # Business logic
└── web/               # Helidon glue (JSON support, security headers, session cookies)

frontend/src/
├── jvmMain/kotlin/com/adoptu/site/
│   ├── SiteGenerator.kt   # renders every page to a static .html at build time
│   └── pages/             # kotlinx.html page templates (index, login, profile, ...)
├── jsMain/kotlin/com/adoptu/frontend/   # browser JS: API calls, DOM updates, i18n
└── main/scss/                          # site styling (compiled by :frontend:compileSass)
```

`./gradlew :frontend:generateSite` renders `frontend/src/jvmMain/.../site/pages/*.kt` to
`frontend/build/site/*.html`, alongside the compiled CSS/JS - the deployable static site.

## Testing

```bash
./gradlew :backend:test                   # Unit tests
./gradlew dockerUp && ./gradlew integrationTest  # Integration tests
./gradlew e2eTest                         # E2E tests with Playwright in Docker
./gradlew dockerDown                      # Stop test containers
```


# Deploy:
`scripts/deploy.sh` automates everything below (image build/push, tfvars digest pin, `tofu
apply`, static site build/S3 sync, CloudFront invalidation) - use it instead of these manual
steps unless you're debugging the pipeline itself. The steps below only cover the backend image;
they do **not** deploy the static site (see `:frontend:generateSite` and the S3 sync step in
`scripts/deploy.sh`) - following just this section leaves the frontend undeployed.

Retrieve an authentication token and authenticate your Docker client to your registry. Use the AWS CLI:
```
aws ecr get-login-password --region us-east-1 | podman login --username AWS --password-stdin 174000857825.dkr.ecr.us-east-1.amazonaws.com
```
Note: If you receive an error using the AWS CLI, make sure that you have the latest version of the AWS CLI and Docker installed.

Build your Docker image using the following command. For information on building a Docker file from scratch see the instructions here. You can skip this step if your image is already built:
```
podman build -t production/adoptu .
```
After the build completes, tag your image so you can push the image to this repository:
```
podman tag production/adoptu:latest 174000857825.dkr.ecr.us-east-1.amazonaws.com/production/adoptu:latest
```
Run the following command to push this image to your newly created AWS repository:
```
podman push 174000857825.dkr.ecr.us-east-1.amazonaws.com/production/adoptu:latest
```