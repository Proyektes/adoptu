# Adopt-U - Pet Adoption Platform

A Kotlin pet adoption web application with **FIDO2/WebAuthn** passwordless authentication. Multi-page application with HTML rendered from Kotlin (kotlinx.html).

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

- **Backend**: Kotlin, Ktor, Exposed ORM
- **Database**: PostgreSQL
- **Auth**: WebAuthn4J (FIDO2/Passkeys)
- **Frontend**: Kotlin HTML (kotlinx.html DSL), Kotlin/JS
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

```
backend/src/main/kotlin/com/adoptu/
├── Application.kt
├── adapters/          # DB repositories, S3 storage, SES email
├── di/                # Koin dependency injection
├── dto/               # Request/response DTOs
├── pages/             # kotlinx.html page renderers
├── plugins/           # Ktor plugins (Routing, Sessions, etc.)
├── ports/             # Repository/storage/notification interfaces
├── routes/            # Route handlers
└── services/          # Business logic
```

## Testing

```bash
./gradlew :backend:test                   # Unit tests
./gradlew dockerUp && ./gradlew integrationTest  # Integration tests
./gradlew e2eTest                         # E2E tests with Playwright in Docker
./gradlew dockerDown                      # Stop test containers
```


# Deploy:
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