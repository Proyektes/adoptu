resource "aws_s3_bucket" "static_images" {
  bucket = var.static_bucket_name
}

resource "aws_s3_bucket" "dynamic_images" {
  bucket = var.dynamic_bucket_name
}

# Holds the static site build (frontend/build/site/, produced by :frontend:generateSite) -
# uploaded here as a deploy step (aws s3 sync), separate from the backend's own image/deploy
# pipeline. See cloudfront.tf's aws_cloudfront_distribution.app default_cache_behavior.
resource "aws_s3_bucket" "site" {
  bucket = var.site_bucket_name
}

# Access is via CloudFront Origin Access Control only (bucket policies
# below) - no public ACLs/policies are granted, so it's safe to lock these
# down fully. Live buckets currently have public access block disabled;
# tightening it here doesn't change actual reachability.
resource "aws_s3_bucket_public_access_block" "static_images" {
  bucket = aws_s3_bucket.static_images.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "dynamic_images" {
  bucket = aws_s3_bucket.dynamic_images.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "site" {
  bucket = aws_s3_bucket.site.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "static_images" {
  bucket = aws_s3_bucket.static_images.id
  policy = jsonencode({
    Version = "2008-10-17"
    Id      = "PolicyForCloudFrontPrivateContent"
    Statement = [{
      Sid       = "AllowCloudFrontServicePrincipal"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.static_images.arn}/*"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.static_images.arn
        }
      }
    }]
  })
}

resource "aws_s3_bucket_policy" "dynamic_images" {
  bucket = aws_s3_bucket.dynamic_images.id
  policy = jsonencode({
    Version = "2008-10-17"
    Id      = "PolicyForCloudFrontPrivateContent"
    Statement = [{
      Sid       = "AllowCloudFrontServicePrincipal"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.dynamic_images.arn}/*"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.dynamic_images.arn
        }
      }
    }]
  })
}

resource "aws_s3_bucket_policy" "site" {
  bucket = aws_s3_bucket.site.id
  policy = jsonencode({
    Version = "2008-10-17"
    Id      = "PolicyForCloudFrontPrivateContent"
    Statement = [{
      Sid       = "AllowCloudFrontServicePrincipal"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.site.arn}/*"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.app.arn
        }
      }
    }]
  })
}
