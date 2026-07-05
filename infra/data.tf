# Pre-existing resources this stack plugs into. These are read-only lookups;
# none of them are created or destroyed by this config.

data "aws_caller_identity" "current" {}

data "aws_vpc" "target" {
  id      = var.vpc_id
  default = var.vpc_id == null ? true : null
}

data "aws_route53_zone" "primary" {
  name         = "${var.domain_name}."
  private_zone = false
}

# Wildcard cert (*.adopt-u.org + adopt-u.org) already issued and validated;
# used by CloudFront (must be in us-east-1, regardless of var.aws_region).
data "aws_acm_certificate" "wildcard_us_east_1" {
  provider    = aws.us_east_1
  domain      = "*.${var.domain_name}"
  statuses    = ["ISSUED"]
  most_recent = true
}

data "aws_ecr_repository" "backend" {
  name = var.ecr_repository_name
}

data "aws_internet_gateway" "main" {
  filter {
    name   = "attachment.vpc-id"
    values = [data.aws_vpc.target.id]
  }
}

# Auto-created by the RDS console for the default VPC; reused as-is rather
# than re-creating, since the live "adoptu" instance already sits in it.
data "aws_db_subnet_group" "default" {
  name = "default-${data.aws_vpc.target.id}"
}

data "aws_iam_role" "rds_monitoring" {
  name = "rds-monitoring-role"
}

# 45 entries - comfortably under the per-security-group rule quota on its
# own. The IPv6 equivalent list also exists (pl-02d12e369a4312e03) but isn't
# used: combined, the two lists' entries exceed the quota, and CloudFront's
# connection to a custom origin doesn't reliably work over IPv6 in practice
# anyway (it fell back to needing an A record here - see route53.tf) even
# though viewer-facing CloudFront traffic is dual-stack. IPv4-only for the
# origin-facing rule is both simpler and sufficient.
data "aws_ec2_managed_prefix_list" "cloudfront_origin_facing_ipv4" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}
