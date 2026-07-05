# Keeps backend.<domain> (route53.tf) pointed at whichever ECS task is
# currently running, automatically - no manual DNS step after a deploy,
# no load balancer. Fires off every "task reached RUNNING" event for the
# adoptu cluster.

data "archive_file" "dns_updater" {
  type        = "zip"
  source_dir  = "${path.module}/lambda/dns_updater"
  output_path = "${path.module}/lambda/dns_updater.zip"
}

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "dns_updater" {
  name               = "adoptu-dns-updater"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

resource "aws_iam_role_policy_attachment" "dns_updater_logs" {
  role       = aws_iam_role.dns_updater.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "dns_updater" {
  statement {
    sid       = "DescribeTask"
    actions   = ["ecs:DescribeTasks"]
    resources = ["arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:task/${aws_ecs_cluster.this.name}/*"]
  }

  statement {
    sid       = "DescribeService"
    actions   = ["ecs:DescribeServices"]
    resources = ["arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/${aws_ecs_cluster.this.name}/${aws_ecs_service.app.name}"]
  }

  statement {
    sid       = "DescribeTaskEni"
    actions   = ["ec2:DescribeNetworkInterfaces"] # no resource-level permissions exist for this read action
    resources = ["*"]
  }

  statement {
    sid       = "UpdateBackendRecord"
    actions   = ["route53:ChangeResourceRecordSets"]
    resources = ["arn:aws:route53:::hostedzone/${data.aws_route53_zone.primary.zone_id}"]
  }
}

resource "aws_iam_role_policy" "dns_updater" {
  name   = "adoptu-dns-updater"
  role   = aws_iam_role.dns_updater.id
  policy = data.aws_iam_policy_document.dns_updater.json
}

resource "aws_cloudwatch_log_group" "dns_updater" {
  name              = "/aws/lambda/adoptu-dns-updater"
  retention_in_days = 14
}

resource "aws_lambda_function" "dns_updater" {
  function_name = "adoptu-dns-updater"
  role          = aws_iam_role.dns_updater.arn
  handler       = "index.handler"
  runtime       = "python3.12"
  timeout       = 30 # 12s stability wait (see index.py) + API calls, with margin

  filename         = data.archive_file.dns_updater.output_path
  source_code_hash = data.archive_file.dns_updater.output_base64sha256

  environment {
    variables = {
      HOSTED_ZONE_ID = data.aws_route53_zone.primary.zone_id
      RECORD_NAME    = aws_route53_record.backend.name
      SERVICE_NAME   = aws_ecs_service.app.name
    }
  }

  depends_on = [aws_cloudwatch_log_group.dns_updater, aws_iam_role_policy_attachment.dns_updater_logs]
}

resource "aws_cloudwatch_event_rule" "ecs_task_running" {
  name = "adoptu-ecs-task-running"

  event_pattern = jsonencode({
    source      = ["aws.ecs"]
    detail-type = ["ECS Task State Change"]
    detail = {
      clusterArn = [aws_ecs_cluster.this.arn]
      lastStatus = ["RUNNING"]
    }
  })
}

resource "aws_cloudwatch_event_target" "dns_updater" {
  rule = aws_cloudwatch_event_rule.ecs_task_running.name
  arn  = aws_lambda_function.dns_updater.arn
}

resource "aws_lambda_permission" "events_invoke_dns_updater" {
  statement_id  = "AllowEventBridgeInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.dns_updater.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.ecs_task_running.arn
}
