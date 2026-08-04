output "backend_origin_record" {
  description = "Internal DNS name CloudFront's app origin points to - kept current automatically by the dns_updater Lambda, never manually."
  value       = aws_route53_record.backend.fqdn
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "rds_endpoint" {
  value = aws_db_instance.postgres.address
}

output "cloudfront_app_domain" {
  value = aws_cloudfront_distribution.app.domain_name
}

# Read by scripts/deploy.sh (tofu output -raw) to invalidate the cache after syncing
# frontend/build/site/ to aws_s3_bucket.site - the static site's filenames aren't
# content-hashed, so a stale CloudFront edge cache would otherwise keep serving the old
# HTML/CSS/JS until each object's TTL naturally expires.
output "cloudfront_app_distribution_id" {
  value = aws_cloudfront_distribution.app.id
}

output "site_bucket_name" {
  value = aws_s3_bucket.site.bucket
}

output "cloudfront_static_domain" {
  value = aws_cloudfront_distribution.static_images.domain_name
}

output "cloudfront_dynamic_domain" {
  value = aws_cloudfront_distribution.dynamic_images.domain_name
}

output "ecs_ipv6_subnet_ids" {
  value = { for az, s in aws_subnet.ecs_ipv6_only : az => s.id }
}
