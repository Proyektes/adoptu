# New, dedicated cluster rather than reusing the account's existing
# "default" cluster (where the live "Adopt-u-ipv6" service currently runs)
# - keeps this stack's resources cleanly separate from the manually
# console-created ones until cutover. See infra/README.md for the
# migration/cutover plan.

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
      name      = "Main"
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
        { name = "ADOPTU_S3_ENDPOINT", value = "https://${aws_s3_bucket.dynamic_images.bucket_regional_domain_name}" },
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
      ]

      # Was a plaintext environment variable in the live task definition.
      secrets = [
        { name = "ADOPTU_DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_app_password.arn },
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
  name            = "adoptu"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  # No load balancer: CloudFront origins directly to this task over IPv6
  # (see cloudfront.tf / dns_updater.tf), matching the live deployment's
  # design.
  network_configuration {
    subnets          = [for s in aws_subnet.ecs_ipv6_only : s.id]
    security_groups  = [aws_security_group.ecs_task.id]
    assign_public_ip = true # required for ECR pulls (IPv4-only) - see network.tf; matches the live service's own config
  }
}
