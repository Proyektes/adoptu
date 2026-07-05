data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# --- Execution role: pulls the image, ships logs, reads secrets -------------
# Split out from the task role (the live deployment reused one role for
# both, which over-grants the app process AmazonECSTaskExecutionRolePolicy).

resource "aws_iam_role" "ecs_execution" {
  name               = "adoptu-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "ecs_execution_managed" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "ecs_execution_secrets" {
  statement {
    sid     = "ReadAppSecrets"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      aws_secretsmanager_secret.db_app_password.arn,
      aws_secretsmanager_secret.session_secret.arn,
    ]
  }
}

resource "aws_iam_role_policy" "ecs_execution_secrets" {
  name   = "adoptu-ecs-execution-secrets"
  role   = aws_iam_role.ecs_execution.id
  policy = data.aws_iam_policy_document.ecs_execution_secrets.json
}

# --- Task role: the app's own AWS permissions (S3 image storage, SES) -------
# Reuses the live role/policy names so `tofu import` attaches to the
# existing objects instead of creating duplicates. The live policy's S3
# statement pointed at the literal placeholder ARN "your-bucket-name" - a
# real bug, fixed here to the actual buckets - and the unused rds-db:connect
# statement is dropped since IAM database authentication isn't enabled on
# the instance (the app authenticates with a Postgres password, see
# db_app_password).

resource "aws_iam_role" "ecs_task" {
  name               = "adoptuAppServicesPerms"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "ecs_task_app" {
  statement {
    sid = "S3Access"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket",
      "s3:GetObjectVersion",
    ]
    resources = [
      aws_s3_bucket.static_images.arn,
      "${aws_s3_bucket.static_images.arn}/*",
      aws_s3_bucket.dynamic_images.arn,
      "${aws_s3_bucket.dynamic_images.arn}/*",
    ]
  }

  statement {
    sid = "SESAccess"
    actions = [
      "ses:SendEmail",
      "ses:SendRawEmail",
      "ses:SendBulkTemplatedEmail",
      "ses:SendTemplatedEmail",
      "ses:GetSendQuota",
      "ses:GetSendStatistics",
      "ses:ListIdentities",
      "ses:GetIdentityVerificationAttributes",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "ecs_task_app" {
  name   = "adoptuAppServicesPermsPolicy"
  policy = data.aws_iam_policy_document.ecs_task_app.json
}

resource "aws_iam_role_policy_attachment" "ecs_task_app" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = aws_iam_policy.ecs_task_app.arn
}
