# New, dedicated cluster rather than reusing the account's existing
# "default" cluster (where the live "Adopt-u-ipv6" service currently runs)
# - keeps this stack's resources cleanly separate from the manually
# console-created ones until cutover. See infra/README.md for the
# migration/cutover plan.

locals {
  # Runtime profile by task size (see "Runtime profile by task size" in AGENTS.md for the full rule
  # table and rationale):
  #   < 1 vCPU (< 1024 cpu units):        native image, Serial GC (build-time choice)
  #   1-2 vCPU (1024-2047 cpu units):     native image, G1 GC (build-time choice)
  #   >= 2 vCPU and >= 2GB, long-lived:   JVM (JDK 25), G1 + AOTCache at runtime
  runtime_profile = (
    var.task_cpu >= 2048 && var.task_memory >= 2048 ? "jvm" :
    var.task_cpu >= 1024 ? "native-g1" :
    "native-serial"
  )

  # Same heap cap for every profile: 60% of task memory, whether that's Substrate VM's
  # -XX:MaximumHeapSizePercent (native) or a computed -Xmx (JVM).
  heap_percent = 60

  # Only meaningful for the "jvm" profile -- the native runtime stage doesn't read JAVA_OPTS, only
  # HEAP_PERCENT (see root Dockerfile's native ENTRYPOINT).
  runtime_java_opts = local.runtime_profile == "jvm" ? join(" ", [
    "-XX:+UseG1GC",
    "-Xmx${floor(var.task_memory * local.heap_percent / 100)}m",
    "-XX:MaxMetaspaceSize=96m",
    "-XX:ReservedCodeCacheSize=64m",
    "-XX:+UseCompactObjectHeaders",
    # -XX:AOTCache=app.aot intentionally omitted until a training run produces app.aot in the
    # image -- see root Dockerfile's jvm stage TODO(AOTCache).
  ]) : ""
}

# Fails `tofu plan` if var.task_cpu/var.task_memory and runtime_profile ever drift apart, e.g. a
# future resize to 2 vCPU / 2GB without updating the profile logic above, or the reverse (jvm
# profile force-picked on a task too small for it).
check "runtime_profile_matches_task_size" {
  assert {
    condition     = !(local.runtime_profile == "jvm" && var.task_memory < 2048)
    error_message = "runtime_profile is 'jvm' but task_memory (${var.task_memory} MiB) is below the 2048 MiB floor the JVM profile assumes -- see 'Runtime profile by task size' in AGENTS.md."
  }
  assert {
    condition     = !(local.runtime_profile != "jvm" && var.task_cpu >= 2048 && var.task_memory >= 2048)
    error_message = "Task is >= 2 vCPU / >= 2GB but runtime_profile resolved to '${local.runtime_profile}' instead of 'jvm' -- check the runtime_profile expression in infra/ecs.tf."
  }
}

resource "aws_ecs_cluster" "this" {
  name = "adoptu"
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/adoptu"
  retention_in_days = 30
}

resource "aws_ecs_task_definition" "app" {
  family                   = "adoptu"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name = "Main"
      # container_image_tag may be a tag ("latest") or a digest
      # ("sha256:..."), per its description - digests need an "@" separator,
      # tags need ":". Without this, a digest value produces an invalid
      # reference like "repo:sha256:abc" that ECS rejects at task startup.
      image     = "${data.aws_ecr_repository.backend.repository_url}${startswith(var.container_image_tag, "sha256:") ? "@" : ":"}${var.container_image_tag}"
      essential = true

      # App listens on container_port (8080 by default, per Dockerfile/
      # application.conf) - the live task definition mapped port 80, which
      # didn't match what the app actually binds to.
      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "ADOPTU_ENV", value = "prod" },
        { name = "ADOPTU_PORT", value = tostring(var.container_port) },
        { name = "ADOPTU_ADMIN_EMAIL", value = var.admin_email },
        { name = "ADOPTU_ADMIN_USERNAME", value = var.admin_username },
        { name = "ADOPTU_DB_URL", value = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/${var.db_app_database_name}" },
        { name = "ADOPTU_DB_USER", value = "adoptu" },
        { name = "ADOPTU_S3_BUCKET", value = aws_s3_bucket.dynamic_images.bucket },
        { name = "ADOPTU_S3_REGION", value = var.aws_region },
        # The bucket blocks direct public access (policy only allows the CloudFront
        # distribution) - image URLs returned to clients must be the CDN domain, not any
        # S3-derived host, or the browser gets a 403 loading every photo.
        { name = "ADOPTU_S3_PUBLIC_URL", value = "https://dynamic.${var.domain_name}" },
        # No ADOPTU_S3_ENDPOINT here on purpose: S3ImageStorageAdapter uses
        # virtual-hosted-style addressing (path_style_access = false), which
        # prepends the bucket name onto whatever endpoint it's given. Setting
        # this to bucket_regional_domain_name (already bucket-specific,
        # "adoptu-dynamic-images.s3.<region>.amazonaws.com") doubled the
        # bucket name into an invalid host and broke every upload with a TLS
        # hostname-mismatch error. The AWS SDK resolves the correct
        # regional/virtual-hosted endpoint on its own with no override at
        # all - an endpoint override is only needed for LocalStack in dev
        # (see application.conf's storage.dev block).
        { name = "AWS_REGION", value = var.aws_region },
        { name = "AWS_SES_ENDPOINT", value = "https://email.${var.aws_region}.amazonaws.com" },
        # Never set before - every outbound-email action link (password
        # reset, magic-link login, email/profile-email verification,
        # temporal-home spam-report) defaulted to application.conf's
        # http://localhost:80 in production as a result.
        { name = "ADOPTU_BASE_URL", value = var.base_url },
        # Plural: application.conf's "webauthn.origins" is a HOCON list,
        # substituted raw from this env var - it must stay a JSON/HOCON
        # array string, not a bare origin. The live task definition had this
        # as "ADOPTU_WEB_AUTHN_ORIGIN" (singular), which application.conf
        # never actually reads - the app silently fell back to its default
        # (["http://localhost:8080"]) in production, so WebAuthn origin
        # validation likely never worked for real users on the old image.
        { name = "ADOPTU_WEB_AUTHN_ORIGINS", value = var.webauthn_origins },
        { name = "ADOPTU_WEB_AUTHN_RP_ID", value = var.webauthn_rp_id },
        { name = "ADOPTU_DEPLOY_SEQUENCE", value = var.deploy_sequence },
        # Runtime sizing derived from local.runtime_profile (var.task_cpu/var.task_memory above) --
        # see "Runtime profile by task size" in AGENTS.md. HEAP_PERCENT feeds the native image's
        # -XX:MaximumHeapSizePercent (root Dockerfile's ENTRYPOINT); JAVA_OPTS is only non-empty
        # for the "jvm" profile.
        { name = "HEAP_PERCENT", value = tostring(local.heap_percent) },
        { name = "JAVA_OPTS", value = local.runtime_java_opts },
      ]

      # Was a plaintext environment variable in the live task definition.
      secrets = [
        { name = "ADOPTU_DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_app_password.arn },
        { name = "ADOPTU_SESSION_SECRET", valueFrom = aws_secretsmanager_secret.session_secret.arn },
        # Urgent Rescuer anonymous-report CAPTCHA - see UrgentRescueService, TurnstileCaptchaAdapter.
        # (SMS paging uses AWS SNS via the ECS task role - see SnsSmsAdapter, infra/iam.tf's
        # SNSAccess statement - no separate credential needed, unlike Twilio's auth token.)
        { name = "ADOPTU_TURNSTILE_SECRET_KEY", valueFrom = aws_secretsmanager_secret.turnstile_secret_key.arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "app" {
  name                   = "adoptu"
  cluster                = aws_ecs_cluster.this.id
  task_definition        = aws_ecs_task_definition.app.arn
  desired_count          = var.desired_count
  launch_type            = "FARGATE"
  enable_execute_command = true # debugging aid, added while diagnosing SES credential-vending failure (2026-07-11)

  # No load balancer: CloudFront origins directly to this task over IPv6
  # (see cloudfront.tf / dns_updater.tf), matching the live deployment's
  # design.
  network_configuration {
    subnets          = [for s in aws_subnet.ecs_ipv6_only : s.id]
    security_groups  = [aws_security_group.ecs_task.id]
    assign_public_ip = true # required for ECR pulls (IPv4-only) - see network.tf; matches the live service's own config
  }
}
