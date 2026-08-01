#!/bin/bash
# Builds the backend image from the current git HEAD, pushes it to ECR by
# digest, pins that digest in infra/terraform.tfvars (with the HEAD commit
# subject as the tfvars comment, matching existing entries), and rolls it
# out via OpenTofu.
#
# Defaults to a dry run (build + push + `tofu plan` only, no apply) so the
# plan can be reviewed. Pass --yes to actually apply and roll the change out
# to the live ECS service.
#
# Any uncommitted local changes are auto-stashed before the build (so a
# dirty working tree never leaks into the deployed image) and restored
# afterwards - deploy exactly what's on HEAD, nothing else.
set -euo pipefail

cd "$(dirname "$0")/.."

AWS_PROFILE="${AWS_PROFILE:-adoptu}"
AWS_REGION="${AWS_REGION:-us-east-1}"
ECR_REPO="${ECR_REPO:-174000857825.dkr.ecr.us-east-1.amazonaws.com/production/adoptu}"
ECS_CLUSTER="${ECS_CLUSTER:-adoptu}"
ECS_SERVICE="${ECS_SERVICE:-adoptu}"

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

echo "==> Deploying HEAD ($IMAGE_TAG): $COMMIT_SUBJECT"

echo "==> Logging in to ECR ($AWS_REGION, profile $AWS_PROFILE)"
aws ecr get-login-password --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  | podman login --username AWS --password-stdin "${ECR_REPO%%/*}"

echo "==> Building $ECR_REPO:$IMAGE_TAG"
# GITHUB_ACTOR/PAYMENT_KIT_TOKEN/AUTH_KIT_TOKEN authenticate the private GitHub Packages repos
# (EmailKit/RateLimitKit, AuthKit) the backend depends on - same names ~/.profile exports for
# host-side Gradle builds. Passed as build secrets (never --build-arg) so they never land in the
# image's layer history. Dockerfile's builder-stage RUN steps consume these ids.
: "${GITHUB_ACTOR:?GITHUB_ACTOR must be set (see ~/.profile)}"
: "${PAYMENT_KIT_TOKEN:?PAYMENT_KIT_TOKEN must be set (see ~/.profile)}"
: "${AUTH_KIT_TOKEN:?AUTH_KIT_TOKEN must be set (see ~/.profile)}"
podman build \
  --secret id=github_actor,env=GITHUB_ACTOR \
  --secret id=payment_kit_token,env=PAYMENT_KIT_TOKEN \
  --secret id=auth_kit_token,env=AUTH_KIT_TOKEN \
  -t "$ECR_REPO:$IMAGE_TAG" -t "$ECR_REPO:latest" .

echo "==> Pushing $ECR_REPO:$IMAGE_TAG and :latest"
podman push "$ECR_REPO:$IMAGE_TAG"
podman push "$ECR_REPO:latest"

DIGEST="$(aws ecr describe-images --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  --repository-name "${ECR_REPO#*/}" --image-ids imageTag="$IMAGE_TAG" \
  --query 'imageDetails[0].imageDigest' --output text)"

echo "==> Pushed digest: $DIGEST"

TFVARS="infra/terraform.tfvars"
python3 - "$TFVARS" "$DIGEST" "$COMMIT_SUBJECT" <<'PY'
import re, sys

path, digest, subject = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path) as f:
    content = f.read()

new_line = f'container_image_tag   = "{digest}" # {subject}'
content, n = re.subn(r'^container_image_tag\s*=.*$', new_line, content, count=1, flags=re.MULTILINE)
if n != 1:
    sys.exit(f"container_image_tag line not found in {path}")

with open(path, "w") as f:
    f.write(content)
PY

echo "==> Updated $TFVARS:"
grep container_image_tag "$TFVARS"

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

echo "==> Waiting for ECS service to reach steady state..."
aws ecs wait services-stable --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE"

echo "==> Deployment complete: $ECR_REPO:$IMAGE_TAG ($COMMIT_SUBJECT)"
