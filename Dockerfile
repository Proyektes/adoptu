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
RUN chmod +x gradlew

COPY backend/src backend/src
COPY frontend/src frontend/src

# CSS is precompiled from SCSS by hand and committed under
# backend/src/main/resources/static/css/ - there is no Gradle Sass task.
#
# :backend:jar and :backend:nativeCompile run as two separate --no-daemon
# invocations, not one combined build: native-image claims ~80% of container
# memory for itself, and if the Kotlin compiler's own JVM heap is still alive
# from the same Gradle invocation the build gets OOM-killed (exit 137) in a
# constrained-memory CI container. Running them separately lets the first
# JVM fully exit before native-image starts.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :backend:jar --no-daemon
RUN --mount=type=cache,target=/root/.gradle \
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

COPY --from=builder /app/backend/build/native/nativeCompile/adoptu-backend .
COPY backend/src/main/resources/application.conf .

ENV ADOPTU_ENV="prod"

USER app
EXPOSE 8080

ENTRYPOINT ["./adoptu-backend"]
