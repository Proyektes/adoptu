# syntax=docker/dockerfile:1

# Build stage - musl/Alpine-based JDK so the jlinked runtime below is ABI
# compatible with the musl Alpine base used in the runtime stage (a glibc
# JDK jlinked onto musl Alpine fails to exec at all).
FROM docker.io/amazoncorretto:25-alpine-jdk AS builder

WORKDIR /app

# findutils provides xargs (used by the Gradle wrapper script);
# binutils provides objcopy (used by jlink --strip-debug below).
RUN apk add --no-cache findutils binutils

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
# backend/src/main/resources/static/css/ - there is no Gradle Sass task,
# and shadowJar already pulls in compileKotlin/processResources transitively.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :backend:shadowJar --no-daemon

# jdeps-derive the JDK modules the fat jar actually needs, plus a small
# safety-net set that static bytecode analysis misses: TLS ECDSA cipher
# suites and DNS-based JNDI used by the AWS SDK, jdk.unsupported for
# Netty's reflective sun.misc.Unsafe access, and jdk.zipfs for Helidon's
# runtime classpath metadata scanning (it opens its own jar as a
# java.nio.file filesystem via FileSystems.newFileSystem, which throws
# ProviderNotFoundException without this module - jdeps' static analysis
# doesn't see this reflective use).
RUN --mount=type=cache,target=/root/.gradle \
    MODULES=$(jdeps --multi-release 25 --print-module-deps --ignore-missing-deps \
      backend/build/libs/*-all.jar) \
    && jlink \
      --module-path "$JAVA_HOME/jmods" \
      --add-modules "${MODULES},jdk.crypto.ec,jdk.naming.dns,jdk.unsupported,java.naming,jdk.zipfs" \
      --strip-debug --no-header-files --no-man-pages --compress=zip-9 \
      --output /jre

# Runtime stage - custom jlink JRE on Alpine, no full JDK needed.
# Must match the builder's Alpine version for musl ABI compatibility.
FROM docker.io/alpine:3.24

RUN apk add --no-cache libstdc++ \
    && addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=builder /jre /opt/jre
COPY --from=builder /app/backend/build/libs/*-all.jar app.jar
COPY backend/src/main/resources/application.conf .

ENV PATH="/opt/jre/bin:${PATH}"
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 --enable-native-access=ALL-UNNAMED"
ENV ADOPTU_ENV="prod"

USER app
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
