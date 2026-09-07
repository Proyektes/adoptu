#!/bin/bash
# Builds the backend image from the current git HEAD, pushes it to ECR by
# digest, pins that digest in infra/terraform.tfvars (with the HEAD commit
# subject as the tfvars comment, matching existing entries), and rolls it
# out via OpenTofu. Also builds the static site (:frontend:generateSite) and,
# once the infra apply confirms aws_s3_bucket.site exists, syncs it there and
# invalidates the CloudFront distribution's edge cache (the site's filenames
# aren't content-hashed, so a stale edge cache wouldn't otherwise notice).
#
# Defaults to a dry run (build + push + `tofu plan` only, no apply, no S3
# sync) so the plan can be reviewed. Pass --yes to actually apply and roll
# the change out to the live ECS service + static site.
#
# Any uncommitted local changes are auto-stashed before the build (so a
# dirty working tree never leaks into the deployed image/site) and restored
# afterwards - deploy exactly what's on HEAD, nothing else.
set -euo pipefail

cd "$(dirname "$0")/.."
REPO_ROOT="$(pwd)"

AWS_PROFILE="${AWS_PROFILE:-adoptu}"
AWS_REGION="${AWS_REGION:-us-east-1}"
ECR_REPO="${ECR_REPO:-174000857825.dkr.ecr.us-east-1.amazonaws.com/production/adoptu}"
ECS_CLUSTER="${ECS_CLUSTER:-adoptu}"
ECS_SERVICE="${ECS_SERVICE:-adoptu}"
DOMAIN_NAME="${DOMAIN_NAME:-adopt-u.org}"
SITE_DIR="$REPO_ROOT/frontend/build/site"

APPLY=false
for arg in "$@"; do
  case "$arg" in
    --yes|--apply) APPLY=true ;;
  esac
done

STASHED=false
cleanup() {
  if [ "$STASHED" = true ]; then
    echo "==> Restoring stashed local changes"
    git stash pop
  fi
}
trap cleanup EXIT

if [ -n "$(git status --porcelain)" ]; then
  echo "==> Working tree has uncommitted changes - stashing so the image builds from HEAD only"
  git stash push -u -m "deploy.sh: auto-stash before build"
  STASHED=true
fi

IMAGE_TAG="$(git rev-parse --short HEAD)"
COMMIT_SUBJECT="$(git log -1 --format=%s)"

# Numbered deploy counter (mirrors Mazmobi's DEPLOY_SEQUENCE pattern): a plain integer committed
# to the repo-root DEPLOY_SEQUENCE file, incremented for this deploy and baked into
# infra/terraform.tfvars below. Only written back + committed to DEPLOY_SEQUENCE once the
# post-deploy health check (further down) confirms it's actually live, so a failed/aborted deploy
# never burns a sequence number.
CURRENT_DEPLOY_SEQUENCE="$(cat DEPLOY_SEQUENCE 2>/dev/null || echo 0)"
NEW_DEPLOY_SEQUENCE=$((CURRENT_DEPLOY_SEQUENCE + 1))

echo "==> Deploying HEAD ($IMAGE_TAG): $COMMIT_SUBJECT (deploy #$NEW_DEPLOY_SEQUENCE)"

echo "==> Building static site"
./gradlew :frontend:generateSite

echo "==> Logging in to ECR ($AWS_REGION, profile $AWS_PROFILE)"
aws ecr get-login-password --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  | podman login --username AWS --password-stdin "${ECR_REPO%%/*}"

echo "==> Building $ECR_REPO:$IMAGE_TAG"
# GITHUB_ACTOR/PAYMENT_KIT_TOKEN/AUTH_KIT_TOKEN/STORAGE_KIT_TOKEN/IMAGE_KIT_TOKEN authenticate
# the private GitHub Packages repos (EmailKit/RateLimitKit, AuthKit, StorageKit, ImageKit) the
# backend depends on - same names ~/.profile exports for host-side Gradle builds. Passed as build
# secrets (never --build-arg) so they never land in the image's layer history. Dockerfile's
# builder-stage RUN steps consume these ids.
: "${GITHUB_ACTOR:?GITHUB_ACTOR must be set (see ~/.profile)}"
: "${PAYMENT_KIT_TOKEN:?PAYMENT_KIT_TOKEN must be set (see ~/.profile)}"
: "${AUTH_KIT_TOKEN:?AUTH_KIT_TOKEN must be set (see ~/.profile)}"
: "${STORAGE_KIT_TOKEN:?STORAGE_KIT_TOKEN must be set (see ~/.profile)}"
: "${IMAGE_KIT_TOKEN:?IMAGE_KIT_TOKEN must be set (see ~/.profile)}"
podman build \
  --secret id=github_actor,env=GITHUB_ACTOR \
  --secret id=payment_kit_token,env=PAYMENT_KIT_TOKEN \
  --secret id=auth_kit_token,env=AUTH_KIT_TOKEN \
  --secret id=storage_kit_token,env=STORAGE_KIT_TOKEN \
  --secret id=image_kit_token,env=IMAGE_KIT_TOKEN \
  -t "$ECR_REPO:$IMAGE_TAG" -t "$ECR_REPO:latest" .

echo "==> Pushing $ECR_REPO:$IMAGE_TAG and :latest"
podman push "$ECR_REPO:$IMAGE_TAG"
podman push "$ECR_REPO:latest"

DIGEST="$(aws ecr describe-images --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  --repository-name "${ECR_REPO#*/}" --image-ids imageTag="$IMAGE_TAG" \
  --query 'imageDetails[0].imageDigest' --output text)"

echo "==> Pushed digest: $DIGEST"

TFVARS="infra/terraform.tfvars"
python3 - "$TFVARS" "$DIGEST" "$COMMIT_SUBJECT" "$NEW_DEPLOY_SEQUENCE" <<'PY'
import re, sys

path, digest, subject, deploy_sequence = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
with open(path) as f:
    content = f.read()

new_image_line = f'container_image_tag   = "{digest}" # {subject}'
content, n = re.subn(r'^container_image_tag\s*=.*$', new_image_line, content, count=1, flags=re.MULTILINE)
if n != 1:
    sys.exit(f"container_image_tag line not found in {path}")

new_sequence_line = f'deploy_sequence       = "{deploy_sequence}" # kept in sync with the repo-root DEPLOY_SEQUENCE file by deploy.sh'
content, n = re.subn(r'^deploy_sequence\s*=.*$', new_sequence_line, content, count=1, flags=re.MULTILINE)
if n != 1:
    sys.exit(f"deploy_sequence line not found in {path}")

with open(path, "w") as f:
    f.write(content)
PY

echo "==> Updated $TFVARS:"
grep -E "container_image_tag|deploy_sequence" "$TFVARS"

cd infra
echo "==> tofu plan"
tofu plan -no-color

if [ "$APPLY" = false ]; then
  echo
  echo "Dry run only (build + push + plan). Re-run with --yes to apply this plan and roll out to ECS."
  exit 0
fi

echo "==> tofu apply"
tofu apply -auto-approve -no-color

SITE_BUCKET="$(tofu output -raw site_bucket_name)"
DISTRIBUTION_ID="$(tofu output -raw cloudfront_app_distribution_id)"

cd "$REPO_ROOT"

echo "==> Syncing $SITE_DIR to s3://$SITE_BUCKET"
# --delete removes objects from the bucket that no longer exist in the build output (a page
# renamed/removed since the last deploy would otherwise linger and stay reachable indefinitely).
# HTML gets a short max-age since filenames aren't content-hashed (the CloudFront invalidation
# below handles the immediate cutover; this bounds how stale a *client's own* cached copy of an
# HTML page can get if invalidation is ever skipped) - CSS/JS get a longer one since they're still
# far more frequently replaced than truly immutable fingerprinted assets would be.
aws s3 sync "$SITE_DIR" "s3://$SITE_BUCKET" \
  --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  --delete \
  --exclude "*.html" --exclude "serve.json" \
  --cache-control "public, max-age=3600"
aws s3 sync "$SITE_DIR" "s3://$SITE_BUCKET" \
  --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  --delete \
  --exclude "*" --include "*.html" \
  --cache-control "public, max-age=60" --content-type "text/html; charset=utf-8"

echo "==> Invalidating CloudFront distribution $DISTRIBUTION_ID"
aws cloudfront create-invalidation --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  --distribution-id "$DISTRIBUTION_ID" --paths "/*" >/dev/null

echo "==> Waiting for ECS service to reach steady state..."
aws ecs wait services-stable --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE"

# ── Health check ─────────────────────────────────────────────────────────
# Confirms deploy #$NEW_DEPLOY_SEQUENCE is actually the code answering requests, not just that the
# ECS service reached "stable" (which only means the task passed its container health check, not
# that this specific release is live behind CloudFront's cache/DNS). Goes through
# api.$DOMAIN_NAME (CloudFront), not the task's raw IP - the ECS security group only allows
# inbound :8080 from CloudFront's managed prefix list, so a direct-to-IP check would always fail
# regardless of task health.
echo "==> Health-checking https://api.$DOMAIN_NAME/api/version (through CloudFront)..."
HEALTH_OK=false
for i in $(seq 1 10); do
  RESPONSE="$(curl -sf --connect-timeout 5 "https://api.$DOMAIN_NAME/api/version" 2>/dev/null || true)"
  LIVE_SEQUENCE="$(python3 -c "import json,sys; print(json.loads(sys.argv[1]).get('deploySequence',''))" "$RESPONSE" 2>/dev/null || true)"
  if [ "$LIVE_SEQUENCE" = "$NEW_DEPLOY_SEQUENCE" ]; then
    echo "    Health check passed - deploySequence=$LIVE_SEQUENCE"
    HEALTH_OK=true
    break
  fi
  echo "    attempt $i: deploySequence=${LIVE_SEQUENCE:-none} (want $NEW_DEPLOY_SEQUENCE), retrying..."
  sleep 15
done

if [ "$HEALTH_OK" != true ]; then
  echo "ERROR: health check failed after 10 attempts - api.$DOMAIN_NAME never reported deploySequence=$NEW_DEPLOY_SEQUENCE" >&2
  echo "       DEPLOY_SEQUENCE was NOT bumped, so the next deploy will retry this same number." >&2
  exit 1
fi

# ── Bump the committed deploy sequence ──────────────────────────────────────
# Only runs once the health check above has confirmed this deploy is actually live, so a failed
# deploy never advances the persisted sequence - the next attempt just reuses
# NEW_DEPLOY_SEQUENCE. A push failure (e.g. a diverged remote) is surfaced but doesn't fail the
# deploy itself - the app is already live.
echo "==> Bumping DEPLOY_SEQUENCE to $NEW_DEPLOY_SEQUENCE"
echo "$NEW_DEPLOY_SEQUENCE" > DEPLOY_SEQUENCE
git add DEPLOY_SEQUENCE
git commit -m "chore: bump deploy sequence to $NEW_DEPLOY_SEQUENCE" >/dev/null
if git push; then
  echo "    Committed and pushed."
else
  echo "    WARNING: commit succeeded locally but 'git push' failed - push DEPLOY_SEQUENCE manually so the next deploy doesn't reuse $NEW_DEPLOY_SEQUENCE." >&2
fi

echo "==> Deployment complete: $ECR_REPO:$IMAGE_TAG ($COMMIT_SUBJECT), deploy #$NEW_DEPLOY_SEQUENCE, site synced to s3://$SITE_BUCKET"
