#!/usr/bin/env bash
# Load-tests the backend under the SAME resource ceiling as production (ECS Fargate),
# not the dev machine's full CPU/RAM. Dev-machine numbers are meaningless here: the
# 2026-06-30 dispatcher regression (18.2 RPS -> 0.1 RPS, see .wolf/cerebrum.md Decision
# Log) was invisible until tested under --cpus=0.5 --memory=1024m specifically.
#
# Usage:
#   scripts/benchmark.sh <label> [endpoint] [duration_s] [concurrency]
#
#   scripts/benchmark.sh baseline                              # GET /, 30s, 20 concurrent
#   scripts/benchmark.sh hikari-pool "/api/pets?country=United%20States" 30 20
#
# IMPORTANT: change exactly ONE thing between runs (one code change, one config value),
# then re-run with a new label. Bundling two changes into one run misattributes the win
# (or regression) to the wrong change — see .wolf/cerebrum.md lesson history.
#
# Results accumulate in scripts/benchmark-results/ so consecutive runs are comparable.
#
# Requires Docker. Prefers `hey` for load generation, falls back to `wrk`, then `ab`,
# then a plain curl+xargs loop if none are installed. If you don't have Docker, the
# same cgroup constraint can be applied without a container via:
#   systemd-run --user --scope -p MemoryMax=1024M -p CPUQuota=50% ./gradlew :backend:run
set -euo pipefail

LABEL="${1:?Usage: scripts/benchmark.sh <label> [endpoint] [duration_s] [concurrency]}"
ENDPOINT="${2:-/}"
DURATION="${3:-30}"
CONCURRENCY="${4:-20}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESULTS_DIR="$ROOT_DIR/scripts/benchmark-results"
mkdir -p "$RESULTS_DIR"

CONTAINER_NAME="adoptu-benchmark"
PORT="8099"
URL="http://localhost:${PORT}${ENDPOINT}"

cleanup() {
    docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> Building shadow jar"
(cd "$ROOT_DIR" && ./gradlew :backend:shadowJar -q)

JAR=$(find "$ROOT_DIR/backend/build/libs" -name "*-all.jar" | head -1)
if [ -z "$JAR" ]; then
    echo "No shadow jar found under backend/build/libs — build failed?" >&2
    exit 1
fi

echo "==> Starting container: --cpus=0.5 --memory=1024m (matches ECS Fargate task sizing)"
cleanup
# Bridge network + published port + host.docker.internal, not --network host: on Docker
# Desktop (macOS/Windows), --network host attaches to the VM's network namespace, not the
# real host's — it cannot reach a Postgres bound to the real host's 127.0.0.1. This works
# identically on native Linux Docker too, given --add-host for host-gateway.
docker run -d --name "$CONTAINER_NAME" \
    --cpus=0.5 --memory=1024m \
    -p "${PORT}:${PORT}" \
    --add-host=host.docker.internal:host-gateway \
    -e ADOPTU_ENV=prod \
    -e ADOPTU_PORT="$PORT" \
    -e ADOPTU_DB_URL=host.docker.internal:5432 \
    -v "$JAR:/app.jar:ro" \
    amazoncorretto:25-alpine-jdk \
    java -jar /app.jar >/dev/null

echo "==> Waiting for the app to become healthy on :${PORT}"
for i in $(seq 1 30); do
    if curl -sf "http://localhost:${PORT}/" >/dev/null 2>&1; then
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "App never became healthy — check: docker logs $CONTAINER_NAME" >&2
        docker logs "$CONTAINER_NAME" >&2 || true
        exit 1
    fi
    sleep 1
done

TIMESTAMP=$(date -u +"%Y%m%dT%H%M%SZ" 2>/dev/null || echo "run")
OUT_FILE="$RESULTS_DIR/${TIMESTAMP}_${LABEL}.txt"

echo "==> Load-testing $URL for ${DURATION}s at concurrency ${CONCURRENCY}"
{
    echo "label: $LABEL"
    echo "endpoint: $ENDPOINT"
    echo "duration_s: $DURATION"
    echo "concurrency: $CONCURRENCY"
    echo "cgroup: --cpus=0.5 --memory=1024m"
    echo "---"
} > "$OUT_FILE"

if command -v hey >/dev/null 2>&1; then
    hey -z "${DURATION}s" -c "$CONCURRENCY" "$URL" | tee -a "$OUT_FILE"
elif command -v wrk >/dev/null 2>&1; then
    wrk -t"$CONCURRENCY" -c"$CONCURRENCY" -d"${DURATION}s" --latency "$URL" | tee -a "$OUT_FILE"
elif command -v ab >/dev/null 2>&1; then
    # ab has no time-based mode; approximate with a large fixed request count.
    ab -n $((CONCURRENCY * DURATION * 5)) -c "$CONCURRENCY" "$URL" | tee -a "$OUT_FILE"
elif docker image inspect williamyeh/wrk >/dev/null 2>&1 || docker pull williamyeh/wrk >/dev/null 2>&1; then
    # No local load generator installed — use a containerized wrk instead of the crude
    # curl+xargs fallback below. Targets host.docker.internal since this container is
    # separate from the app's, same reasoning as the app container's own DB connection.
    DOCKER_URL="http://host.docker.internal:${PORT}${ENDPOINT}"
    docker run --rm --add-host=host.docker.internal:host-gateway williamyeh/wrk \
        -t"$CONCURRENCY" -c"$CONCURRENCY" -d"${DURATION}s" --latency "$DOCKER_URL" | tee -a "$OUT_FILE"
else
    echo "No hey/wrk/ab/docker-wrk found — falling back to a plain curl+xargs loop (rough numbers only)." | tee -a "$OUT_FILE"
    echo "Install 'hey' (https://github.com/rakyll/hey) for real percentile reporting." | tee -a "$OUT_FILE"
    START=$(date +%s)
    END=$((START + DURATION))
    COUNT=0
    while [ "$(date +%s)" -lt "$END" ]; do
        seq 1 "$CONCURRENCY" | xargs -P "$CONCURRENCY" -I{} curl -s -o /dev/null -w "%{time_total}\n" "$URL" >> "${OUT_FILE}.times"
        COUNT=$((COUNT + CONCURRENCY))
    done
    TOTAL_TIME=$(awk '{s+=$1} END {print s}' "${OUT_FILE}.times")
    echo "requests: $COUNT" | tee -a "$OUT_FILE"
    echo "approx RPS: $(awk -v c="$COUNT" -v d="$DURATION" 'BEGIN{printf "%.1f", c/d}')" | tee -a "$OUT_FILE"
    echo "mean latency (s): $(awk -v t="$TOTAL_TIME" -v c="$COUNT" 'BEGIN{printf "%.4f", t/c}')" | tee -a "$OUT_FILE"
    rm -f "${OUT_FILE}.times"
fi

echo "==> Results saved to $OUT_FILE"
echo "==> Prior runs for comparison:"
ls -1 "$RESULTS_DIR" | grep -v "^${TIMESTAMP}" || echo "  (none yet)"
