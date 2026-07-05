#!/bin/bash
# Builds the GraalVM native-image executable for the backend using the
# ghcr.io/graalvm/native-image-community Docker image (no local GraalVM SDK
# needed). Two separate `./gradlew --no-daemon` invocations, not one: running
# `:backend:jar` and `:backend:nativeCompile` as a single Gradle invocation
# left the Kotlin compiler's own JVM heap alive alongside native-image's
# build-time JVM, and native-image (which claims ~80% of container memory
# itself) got OOM-killed (exit 137) in a 7-8GB container. Running them as two
# invocations lets the first JVM fully exit and release its memory before the
# second starts.
set -e

cd "$(dirname "$0")/.."

docker run --rm \
  --entrypoint bash \
  -v "$(pwd)":/workspace \
  -v "${GRADLE_USER_HOME:-$HOME/.gradle}":/root/.gradle \
  -w /workspace \
  ghcr.io/graalvm/native-image-community:25 \
  -lc "./gradlew :backend:jar --no-daemon && ./gradlew :backend:nativeCompile --no-daemon"

echo "Native executable: backend/build/native/nativeCompile/adoptu-backend"
