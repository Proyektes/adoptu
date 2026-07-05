resource "aws_security_group" "ecs_task" {
  name        = "adoptu-ecs-task-sg"
  description = "Adopt-u Fargate tasks - CloudFront origins straight to the task over IPv6, no load balancer"
  vpc_id      = data.aws_vpc.target.id

  # IPv4-only, scoped to CloudFront's own origin-facing ranges. CloudFront
  # cannot resolve a pure IPv6 (AAAA-only) custom origin - it falls back to
  # an A record and connects over IPv4 - so the origin needs an A record
  # (see route53.tf) and IPv4 ingress here. The IPv6 prefix list exists too,
  # but combining both exceeds the security group's rule-count quota (each
  # list expands to dozens of entries), and CloudFront's origin connection
  # doesn't reliably use IPv6 in practice anyway - IPv4-only is simpler and
  # sufficient.
  ingress {
    description     = "App port from CloudFront (direct origin, no LB)"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront_origin_facing_ipv4.id]
  }

  egress {
    description      = "ECR pulls, CloudWatch Logs, Secrets Manager, SES, S3 - all over IPv6 via IGW, no NAT"
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  tags = {
    Name = "adoptu-ecs-task-sg"
  }
}

resource "aws_security_group" "rds" {
  name        = "adoptu-rds-sg"
  description = "Adopt-u Postgres - only reachable from ECS tasks"
  vpc_id      = data.aws_vpc.target.id

  ingress {
    description     = "Postgres from ECS tasks only"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_task.id]
  }

  # Transitional: the old hand-managed "Adopt-u-ipv6" service (cluster
  # "default", sg-0b2d64479930a2ce1 as of writing) must keep reaching the
  # same RDS instance until it's decommissioned (README Step 4), or it loses
  # DB connectivity the moment this SG is attached to the instance - before
  # CloudFront has even finished propagating the cutover. Remove this block
  # (or set legacy_ecs_task_sg_id to null) once the old service is deleted.
  dynamic "ingress" {
    for_each = var.legacy_ecs_task_sg_id != null ? [var.legacy_ecs_task_sg_id] : []
    content {
      description     = "Postgres from the old pre-cutover ECS service (temporary, remove after decommission)"
      from_port       = 5432
      to_port         = 5432
      protocol        = "tcp"
      security_groups = [ingress.value]
    }
  }

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  tags = {
    Name = "adoptu-rds-sg"
  }
}
