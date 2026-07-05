# Only the records tied to resources this stack manages. The zone's other
# ~20 records (mail MX/SPF/DKIM/DMARC for Hostinger, SES verification
# TXT/DKIM, NS/SOA, the ACM validation CNAME) are already correct and
# deliberately left untouched here - see infra/README.md.

locals {
  app_hostnames = [var.domain_name, "www.${var.domain_name}", "api.${var.domain_name}"]
}

# Internal-only hostname (never seen by a browser) - points at whichever
# Fargate task is currently running. The CloudFront "app" origin
# (cloudfront.tf) targets this name directly rather than a load balancer.
# api.${var.domain_name} stays a public, CloudFront-fronted alias below
# (app_a/app_aaaa) - it is NOT this record, those are two different DNS
# names by necessity (one aliases to CloudFront, this one resolves
# directly to the task).
#
# This is a brand new name (nothing to import). `ignore_changes` because
# its value is kept current automatically by the Lambda in
# dns_updater.tf - reacting to ECS "task RUNNING" events - not by
# Terraform.
resource "aws_route53_record" "backend" {
  zone_id = data.aws_route53_zone.primary.zone_id
  name    = "backend.${var.domain_name}"
  type    = "AAAA"
  ttl     = 60
  records = ["2600:1f18:4f77:4e01:386a:77e7:3b9e:7605"] # seeded from the current live task; the dns_updater Lambda takes over from here

  lifecycle {
    ignore_changes = [records]
  }
}

# CloudFront cannot resolve a pure IPv6 (AAAA-only) custom origin - it falls
# back to an A record and connects over IPv4 (discovered the hard way during
# cutover: 502s until this record existed). The dns_updater Lambda keeps
# this current alongside the AAAA record above on every deploy.
resource "aws_route53_record" "backend_a" {
  zone_id = data.aws_route53_zone.primary.zone_id
  name    = "backend.${var.domain_name}"
  type    = "A"
  ttl     = 60
  records = ["100.31.136.205"] # seeded from the current live task; the dns_updater Lambda takes over from here

  lifecycle {
    ignore_changes = [records]
  }
}

resource "aws_route53_record" "app_a" {
  for_each = toset(local.app_hostnames)

  zone_id = data.aws_route53_zone.primary.zone_id
  name    = each.key
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.app.domain_name
    zone_id                = aws_cloudfront_distribution.app.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "app_aaaa" {
  for_each = toset(local.app_hostnames)

  zone_id = data.aws_route53_zone.primary.zone_id
  name    = each.key
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.app.domain_name
    zone_id                = aws_cloudfront_distribution.app.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "static_a" {
  zone_id = data.aws_route53_zone.primary.zone_id
  name    = "static.${var.domain_name}"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.static_images.domain_name
    zone_id                = aws_cloudfront_distribution.static_images.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "static_aaaa" {
  zone_id = data.aws_route53_zone.primary.zone_id
  name    = "static.${var.domain_name}"
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.static_images.domain_name
    zone_id                = aws_cloudfront_distribution.static_images.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "dynamic_a" {
  zone_id = data.aws_route53_zone.primary.zone_id
  name    = "dynamic.${var.domain_name}"
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.dynamic_images.domain_name
    zone_id                = aws_cloudfront_distribution.dynamic_images.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "dynamic_aaaa" {
  zone_id = data.aws_route53_zone.primary.zone_id
  name    = "dynamic.${var.domain_name}"
  type    = "AAAA"

  alias {
    name                   = aws_cloudfront_distribution.dynamic_images.domain_name
    zone_id                = aws_cloudfront_distribution.dynamic_images.hosted_zone_id
    evaluate_target_health = false
  }
}
