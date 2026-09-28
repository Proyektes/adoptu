# syntax=docker/dockerfile:1

# Build stage - GraalVM native-image. Dynamically linked against glibc (the
# builder image has no musl cross-toolchain, so --static --libc=musl isn't
# available here - see backend/build.gradle.kts's graalvmNative block for
# the accumulated class-init/reflection config under
# backend/src/main/resources/META-INF/native-image/). The runtime stage
# below matches this image's own OS/glibc for ABI compatibility.
# Oracle GraalVM builder: free for production use under the GraalVM Free Terms and Conditions
# (GFTC) licence, and its -O3 uses ML-inferred profiles that measurably beat Community's -O3 on
# startup/throughput. Same JDK 25 / native-image / OS family as the Community image below (kept as
# a commented fallback in case the Oracle Container Registry is ever unreachable from the build host).
FROM container-registry.oracle.com/graalvm/native-image:25 AS builder
# FROM ghcr.io/graalvm/native-image-community:25 AS builder

# BUILD-time GC choice (native-image bakes the collector into the binary, it can't be picked at
# runtime): serial|G1, defaulting to serial for this task's current 0.25-0.5 vCPU / 512MB-1GB
# Fargate size. See "Runtime profile by task size" in AGENTS.md for the full rule table -- pass
# --build-arg NATIVE_GC=G1 once the task grows past 1 vCPU.
ARG NATIVE_GC=serial

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

# Only :backend:jar/:backend:nativeCompile build here - the backend is API-only (no page
# rendering, no CSS/JS). The static site (SCSS/kotlinx.html pages/JS bundle, all now in
# :frontend - see frontend/build.gradle.kts's generateSite task) is a separate deploy pipeline
# (S3 + CloudFront, not this image), so this builder stage has no Sass/Node dependency at all.
#
# :backend:jar and :backend:nativeCompile run as two separate --no-daemon
# invocations, not one combined build: native-image claims ~80% of container
# memory for itself, and if the Kotlin compiler's own JVM heap is still alive
# from the same Gradle invocation the build gets OOM-killed (exit 137) in a
# constrained-memory CI container. Running them separately lets the first
# JVM fully exit before native-image starts.
#
# GITHUB_ACTOR/PAYMENT_KIT_TOKEN/AUTH_KIT_TOKEN/STORAGE_KIT_TOKEN/IMAGE_KIT_TOKEN (same names
# backend/build.gradle.kts's credential() reads, same names exported in ~/.profile for
# host-side builds) authenticate the five private GitHub Packages repos (EmailKit/RateLimitKit,
# AuthKit, StorageKit, ImageKit). Passed as build secrets mounted as files (not --build-arg) so the
# token values never land in image layer history - only this RUN's shell reads them, via a
# subshell `export` from the mounted path. Podman's --mount=type=secret has no env= shorthand
# (unlike Docker buildx), so this file+export form is what works on both. Caller must pass matching
# `podman build --secret id=...,src=...` (or `env=...`, docker) flags (see scripts/deploy.sh).
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=github_actor \
    --mount=type=secret,id=payment_kit_token \
    --mount=type=secret,id=auth_kit_token \
    --mount=type=secret,id=storage_kit_token \
    --mount=type=secret,id=image_kit_token \
    export GITHUB_ACTOR="$(cat /run/secrets/github_actor)" \
      PAYMENT_KIT_TOKEN="$(cat /run/secrets/payment_kit_token)" \
      AUTH_KIT_TOKEN="$(cat /run/secrets/auth_kit_token)" \
      STORAGE_KIT_TOKEN="$(cat /run/secrets/storage_kit_token)" \
      IMAGE_KIT_TOKEN="$(cat /run/secrets/image_kit_token)" && \
    ./gradlew :backend:jar :backend:shadowJar --no-daemon
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=github_actor \
    --mount=type=secret,id=payment_kit_token \
    --mount=type=secret,id=auth_kit_token \
    --mount=type=secret,id=storage_kit_token \
    --mount=type=secret,id=image_kit_token \
    export GITHUB_ACTOR="$(cat /run/secrets/github_actor)" \
      PAYMENT_KIT_TOKEN="$(cat /run/secrets/payment_kit_token)" \
      AUTH_KIT_TOKEN="$(cat /run/secrets/auth_kit_token)" \
      STORAGE_KIT_TOKEN="$(cat /run/secrets/storage_kit_token)" \
      IMAGE_KIT_TOKEN="$(cat /run/secrets/image_kit_token)" && \
    ./gradlew :backend:nativeCompile --no-daemon -PnativeGc=$NATIVE_GC \
      -Porg.gradle.java.installations.paths=$JAVA_HOME \
      -Porg.gradle.java.installations.auto-detect=false \
      -Porg.gradle.java.installations.auto-download=false

# ---------------------------------------------------------------------------
# JVM runtime stage -- `docker build --target jvm` (>= 2 vCPU / >= 2GB, long-lived tasks; see
# "Runtime profile by task size" in AGENTS.md). Reuses the builder's :backend:shadowJar output
# (built above) - the plain :backend:jar is a thin, app-classes-only jar with no bundled
# dependencies and no runnable Main-Class manifest entry (`java -jar` on it crash-loops with
# "no main manifest attribute"); the shadow-produced *-all.jar is the fat jar with a real
# manifest, run directly with `java -jar`.
# ---------------------------------------------------------------------------
FROM amazoncorretto:25-alpine AS jvm

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=builder /app/backend/build/libs/*-all.jar ./adoptu-backend.jar
COPY backend/src/main/resources/application.conf .

ENV ADOPTU_ENV="prod"

USER app
EXPOSE 8080

# JAVA_TOOL_OPTIONS is read by any `java` launcher automatically; JAVA_OPTS is appended explicitly
# below since this is a plain `java -jar`, not a Gradle-generated start script. Set either from the
# ECS task's `environment` block. Suggested baseline for this profile:
#   JAVA_OPTS=-XX:+UseG1GC -Xmx<60% of task memory> -XX:MaxMetaspaceSize=96m \
#             -XX:ReservedCodeCacheSize=64m -XX:+UseCompactObjectHeaders -XX:AOTCache=app.aot
# TODO(AOTCache): app.aot is not produced by this image. It needs a representative
# `-XX:AOTMode=record` training run of the running app (real DB/HTTP traffic) which isn't safe to
# do unattended at image-build time here -- until that training run is scripted, drop
# -XX:AOTCache=app.aot from JAVA_OPTS (an AppCDS `-XX:ArchiveClassesAtExit` fallback on a
# --help/dry-run start is the next thing to try if a real training run stays impractical).
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar adoptu-backend.jar"]

# ---------------------------------------------------------------------------
# Native runtime stage (default target -- `docker build .` with no --target still builds this).
# Same OS family/glibc as the builder (Oracle Linux 10.1, glibc 2.39) for ABI compatibility with
# the dynamically linked native binary. No JDK/JRE needed at all, just CA certs for outbound TLS
# (Postgres/S3/SES).
# ---------------------------------------------------------------------------
FROM docker.io/oraclelinux:10-slim AS native

RUN microdnf install -y ca-certificates shadow-utils \
    && microdnf clean all \
    && groupadd -r app && useradd -r -g app app

WORKDIR /app

# ImageCompressor.kt now uses ImageKit (pure-JVM JPEG/PNG codecs, no java.awt/javax.imageio at
# all - see that file's comment for the incident this replaced: javax.imageio's AWT/Toolkit init
# used to crash native-image on the first PNG upload). native-image can still emit a handful of
# other .so shims (e.g. libjava.so/libjvm.so) unrelated to AWT, so this glob copy stays - it's
# just no longer carrying AWT's shared libraries.
COPY --from=builder /app/backend/build/native/nativeCompile/adoptu-backend .
COPY --from=builder /app/backend/build/native/nativeCompile/*.so .
COPY backend/src/main/resources/application.conf .

ENV ADOPTU_ENV="prod"

USER app
EXPOSE 8080

# -XX:MaximumHeapSizePercent is a Substrate VM runtime option (works with both Serial and G1 native
# images, unlike a JVM -Xmx which native-image has no equivalent flag for) -- HEAP_PERCENT comes
# from the ECS task's `environment` block, sized off task memory. See "Runtime profile by task
# size" in AGENTS.md.
ENTRYPOINT ["/bin/sh", "-c", "exec ./adoptu-backend -XX:MaximumHeapSizePercent=${HEAP_PERCENT:-60}"]
