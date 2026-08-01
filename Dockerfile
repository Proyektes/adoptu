# syntax=docker/dockerfile:1

# Build stage - GraalVM native-image. Dynamically linked against glibc (the
# builder image has no musl cross-toolchain, so --static --libc=musl isn't
# available here - see backend/build.gradle.kts's graalvmNative block for
# the accumulated class-init/reflection config under
# backend/src/main/resources/META-INF/native-image/). The runtime stage
# below matches this image's own OS/glibc for ABI compatibility.
FROM ghcr.io/graalvm/native-image-community:25 AS builder

WORKDIR /app

# Files that change rarely go first so the Gradle dependency layer survives
# source-only edits when Docker layer caching is available.
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
COPY backend/build.gradle.kts backend/build.gradle.kts
COPY frontend/build.gradle.kts frontend/build.gradle.kts
COPY common/build.gradle.kts common/build.gradle.kts
RUN chmod +x gradlew

COPY backend/src backend/src
COPY frontend/src frontend/src
COPY common/src common/src

# CSS is precompiled from SCSS by hand and committed under
# backend/src/main/resources/static/css/ - there is no Gradle Sass task.
#
# :backend:jar and :backend:nativeCompile run as two separate --no-daemon
# invocations, not one combined build: native-image claims ~80% of container
# memory for itself, and if the Kotlin compiler's own JVM heap is still alive
# from the same Gradle invocation the build gets OOM-killed (exit 137) in a
# constrained-memory CI container. Running them separately lets the first
# JVM fully exit before native-image starts.
#
# GITHUB_ACTOR/PAYMENT_KIT_TOKEN/AUTH_KIT_TOKEN (same names backend/build.gradle.kts's
# credential() reads, same names exported in ~/.profile for host-side builds) authenticate the
# three private GitHub Packages repos (EmailKit/RateLimitKit, AuthKit). Passed as build secrets
# mounted as files (not --build-arg) so the token values never land in image layer history -
# only this RUN's shell reads them, via a subshell `export` from the mounted path. Podman's
# --mount=type=secret has no env= shorthand (unlike Docker buildx), so this file+export form is
# what works on both. Caller must pass matching `podman build --secret id=...,src=...` (or
# `env=...`, docker) flags (see scripts/deploy.sh).
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=github_actor \
    --mount=type=secret,id=payment_kit_token \
    --mount=type=secret,id=auth_kit_token \
    export GITHUB_ACTOR="$(cat /run/secrets/github_actor)" \
      PAYMENT_KIT_TOKEN="$(cat /run/secrets/payment_kit_token)" \
      AUTH_KIT_TOKEN="$(cat /run/secrets/auth_kit_token)" && \
    ./gradlew :backend:jar --no-daemon
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=github_actor \
    --mount=type=secret,id=payment_kit_token \
    --mount=type=secret,id=auth_kit_token \
    export GITHUB_ACTOR="$(cat /run/secrets/github_actor)" \
      PAYMENT_KIT_TOKEN="$(cat /run/secrets/payment_kit_token)" \
      AUTH_KIT_TOKEN="$(cat /run/secrets/auth_kit_token)" && \
    ./gradlew :backend:nativeCompile --no-daemon

# Runtime stage - same OS family/glibc as the builder (Oracle Linux 10.1,
# glibc 2.39) for ABI compatibility with the dynamically linked native
# binary. No JDK/JRE needed at all, just CA certs for outbound TLS
# (Postgres/S3/SES).
FROM docker.io/oraclelinux:10-slim

RUN microdnf install -y ca-certificates shadow-utils \
    && microdnf clean all \
    && groupadd -r app && useradd -r -g app app

WORKDIR /app

# javax.imageio's AWT/Toolkit init (used by ImageCompressor for the PNG
# upload path only as of the JPEG codec vendoring - JPEG no longer touches
# AWT at all) dlopen's these at runtime relative to the executable's own
# directory - copying just the binary left them missing entirely
# (UnsatisfiedLinkError: Can't load library: awt), silently breaking every
# photo upload since the native-image migration.
COPY --from=builder /app/backend/build/native/nativeCompile/adoptu-backend .
COPY --from=builder /app/backend/build/native/nativeCompile/*.so .
COPY backend/src/main/resources/application.conf .

ENV ADOPTU_ENV="prod"

USER app
EXPOSE 8080

ENTRYPOINT ["./adoptu-backend"]
