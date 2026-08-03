data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "all_viewer" {
  name = "Managed-AllViewer"
}

# Same forwarding as Managed-AllViewer, plus the CloudFront-Viewer-Country header.
# That header isn't a real viewer-sent header - CloudFront injects it itself from the
# viewer's IP - so it has to be explicitly whitelisted via allViewerAndWhitelistCloudFront;
# Managed-AllViewer alone does not forward it. Used by GET /api/detect-country
# (CountryRoutes.kt) to pre-select the pet-search country dropdown for visitors who
# haven't set one on their profile. Applied only to default_cache_behavior (below) -
# the public listing endpoints don't need it, so they keep plain Managed-AllViewer.
resource "aws_cloudfront_origin_request_policy" "all_viewer_plus_country" {
  name    = "adoptu-all-viewer-plus-viewer-country"
  comment = "Managed-AllViewer plus the CloudFront-Viewer-Country header, for GET /api/detect-country"

  cookies_config {
    cookie_behavior = "all"
  }
  headers_config {
    header_behavior = "allViewerAndWhitelistCloudFront"
    headers {
      items = ["CloudFront-Viewer-Country"]
    }
  }
  query_strings_config {
    query_string_behavior = "all"
  }
}

# Shared by every public, unauthenticated listing endpoint (pets, shelters,
# sterilization-locations, photographers, temporal-homes). Load testing
# GET /api/pets showed origin CPU (JSON serialization + DB query) is the
# throughput bottleneck at realistic concurrency - the same shape applies to
# the other listings. Managed-CachingOptimized isn't used here because it
# drops all query strings from the cache key, but these endpoints vary by
# filters (country/state/city/type/etc.) - this policy forwards all query
# strings instead so filtered requests don't collide with unfiltered ones.
resource "aws_cloudfront_cache_policy" "api_public_listings" {
  name        = "adoptu-api-public-listings"
  comment     = "Public GET listings (pets/shelters/sterilization-locations/photographers/temporal-homes) - vary by query string"
  default_ttl = 30
  min_ttl     = 0
  max_ttl     = 300

  parameters_in_cache_key_and_forwarded_to_origin {
    cookies_config {
      cookie_behavior = "none"
    }
    headers_config {
      header_behavior = "none"
    }
    query_strings_config {
      query_string_behavior = "all"
    }
    enable_accept_encoding_gzip   = true
    enable_accept_encoding_brotli = true
  }
}

resource "aws_cloudfront_origin_access_control" "s3" {
  name                              = "adoptu-s3-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# Clean-URL + dynamic-path-param rewrite for the static site (see
# infra/cloudfront-functions/site-rewrite.js) - same table as scripts/serve_site.py, which serves
# the identical build/site/ output locally.
resource "aws_cloudfront_function" "site_rewrite" {
  name    = "adoptu-site-rewrite"
  runtime = "cloudfront-js-2.0"
  comment = "Clean URLs + /pet/{id} and /temporal-home/{id} rewrites for the static site"
  publish = true
  code    = file("${path.module}/cloudfront-functions/site-rewrite.js")
}

# Static-site equivalent of SecurityHeadersFilter.kt (backend/src/main/kotlin/com/adoptu/web/) -
# the backend dropped these once it stopped serving HTML (JSON API responses don't need them), but
# the static site's HTML responses still do. script-src is a flat 'self' (no nonce/hash): every
# inline <script> in the page templates was moved into common.js during the static-site migration
# (see Shared.kt's commonScripts(), CommonModule.initLocationSearchFilters()/initAuthNav() in
# frontend/Common.kt) specifically so this policy could stay nonce-free - a CDN response header
# can't rotate a nonce per request the way the old per-response Helidon filter did.
resource "aws_cloudfront_response_headers_policy" "site_security_headers" {
  name = "adoptu-site-security-headers"

  security_headers_config {
    content_type_options {
      override = true
    }
    frame_options {
      frame_option = "DENY"
      override     = true
    }
    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }
    strict_transport_security {
      access_control_max_age_sec = 63072000
      include_subdomains         = true
      override                   = true
    }
    content_security_policy {
      content_security_policy = join("; ", [
        "default-src 'self'",
        "script-src 'self'",
        "script-src-attr 'none'",
        "style-src 'self' https://fonts.googleapis.com",
        "font-src 'self' https://fonts.gstatic.com",
        "img-src 'self' data: blob: https://static.adopt-u.org https://dynamic.adopt-u.org https://*.amazonaws.com",
        "connect-src 'self'",
        "object-src 'none'",
        "base-uri 'self'",
        "form-action 'self'",
        "frame-ancestors 'none'",
      ])
      override = true
    }
  }
}

# --- static.adopt-u.org: long-lived static assets --------------------------

resource "aws_cloudfront_distribution" "static_images" {
  enabled         = true
  is_ipv6_enabled = true
  price_class     = "PriceClass_All"
  http_version    = "http2"
  aliases         = ["static.${var.domain_name}"]
  comment         = "adopt-u static images"

  origin {
    domain_name              = aws_s3_bucket.static_images.bucket_regional_domain_name
    origin_id                = "static-images-s3"
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  default_cache_behavior {
    target_origin_id       = "static-images-s3"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = data.aws_acm_certificate.wildcard_us_east_1.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

# --- dynamic.adopt-u.org: user-uploaded pet images ---------------------------

resource "aws_cloudfront_distribution" "dynamic_images" {
  enabled         = true
  is_ipv6_enabled = true
  price_class     = "PriceClass_All"
  http_version    = "http2"
  aliases         = ["dynamic.${var.domain_name}"]
  comment         = "adopt-u dynamic (uploaded) images"

  origin {
    domain_name              = aws_s3_bucket.dynamic_images.bucket_regional_domain_name
    origin_id                = "dynamic-images-s3"
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  default_cache_behavior {
    target_origin_id       = "dynamic-images-s3"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = data.aws_acm_certificate.wildcard_us_east_1.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

# --- adopt-u.org / www / api: the app itself, fronting the ECS task directly -
# No load balancer, by design - same as the live deployment. Origin is the
# internal-only "backend.<domain>" DNS record (route53.tf), which resolves
# straight to the running Fargate task's public IPv6 address and is kept
# current automatically by the dns_updater Lambda (dns_updater.tf) - not
# manually, and not the same name as the public api.<domain> alias below.

resource "aws_cloudfront_distribution" "app" {
  enabled         = true
  is_ipv6_enabled = true
  price_class     = "PriceClass_All"
  http_version    = "http2"
  aliases         = [var.domain_name, "www.${var.domain_name}", "api.${var.domain_name}"]
  comment         = "adopt-u app (static site default, ECS Fargate for /api/* - no load balancer)"

  origin {
    domain_name              = aws_s3_bucket.site.bucket_regional_domain_name
    origin_id                = "site-s3"
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  origin {
    domain_name = "backend.${var.domain_name}"
    origin_id   = "ecs-task"

    custom_origin_config {
      http_port              = var.container_port
      https_port             = 443 # unused (origin_protocol_policy is http-only - matches live, no TLS to origin)
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1", "TLSv1.1", "TLSv1.2"] # unused under http-only; matches live config exactly for clean import
    }
  }

  # Static site (frontend/build/site/, uploaded to aws_s3_bucket.site as a deploy step) is now
  # the default - it used to be the ECS task directly, back when the backend rendered HTML itself
  # (UIRoutes.kt/com.adoptu.pages, removed in the static-site migration). All /api/* traffic is
  # routed to ecs-task via the catch-all ordered_cache_behavior below instead - it MUST stay last
  # among the ordered_cache_behaviors (first-match-wins) so the more specific /api/pets,
  # /api/shelters*, etc. behaviors below still take precedence over it.
  default_cache_behavior {
    target_origin_id           = "site-s3"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.site_security_headers.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.site_rewrite.arn
    }
  }

  # Exact path match only (no wildcard) - a wildcard like "/api/pets/*" would
  # incorrectly sweep in authenticated, user-specific sub-routes like
  # /api/pets/my-adoption-requests - caching those at a shared edge cache
  # would leak one user's data to another. Everything else on this
  # distribution keeps CachingDisabled.
  #
  # POST is required here: PetsRoutes.kt's "post(\"/api/pets\", ...)" (create
  # pet) lives directly on this bare path, not under a sub-path - the prior
  # comment claiming otherwise was wrong, and with POST excluded from
  # allowed_methods CloudFront rejected pet creation outright with a 403
  # before it ever reached the origin (found 2026-07-12 while testing pet
  # creation end-to-end; cached_methods stays GET/HEAD-only so POST is never
  # cached, only forwarded).
  #
  # CloudFront's AllowedMethods only accepts one of three fixed sets:
  # [HEAD,GET], [HEAD,GET,OPTIONS], or the full [HEAD,DELETE,POST,GET,OPTIONS,
  # PUT,PATCH] - there is no "read + POST only" combination, so enabling POST
  # here means accepting the full write-method set too (found 2026-07-12: the
  # previous [GET,HEAD,OPTIONS,POST] value is rejected outright by the
  # CloudFront API with InvalidArgument and was never actually applied - the
  # live distribution had silently stayed on the full set this whole time).
  # PUT/PATCH/DELETE are harmless here even though no route handles them on
  # the bare /api/pets path (Helidon 404s them at the origin same as today).
  ordered_cache_behavior {
    path_pattern             = "/api/pets"
    target_origin_id         = "ecs-task"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = aws_cloudfront_cache_policy.api_public_listings.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  # Wildcard is safe here: every route under /api/shelters (bare listing,
  # /countries, /countries/{c}/states, /{id}) is public and GET-only - the
  # admin CRUD routes live entirely under the separate /api/admin/shelters
  # prefix, so this can never sweep in an authenticated or mutating route.
  ordered_cache_behavior {
    path_pattern             = "/api/shelters*"
    target_origin_id         = "ecs-task"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = aws_cloudfront_cache_policy.api_public_listings.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  # Same reasoning as /api/shelters* - every route under this prefix (bare
  # listing, /grouped, /countries, /countries/{c}/states, .../{s}/cities,
  # /{id}) is public and GET-only; admin CRUD is the separate
  # /api/admin/sterilization-locations prefix.
  ordered_cache_behavior {
    path_pattern             = "/api/sterilization-locations*"
    target_origin_id         = "ecs-task"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = aws_cloudfront_cache_policy.api_public_listings.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  # Exact path match only (no wildcard) - GET /api/photographers/requests
  # (a user's own request list) is authenticated and shares this prefix; a
  # wildcard would risk serving one user's private requests to another from
  # the shared edge cache.
  ordered_cache_behavior {
    path_pattern             = "/api/photographers"
    target_origin_id         = "ecs-task"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = aws_cloudfront_cache_policy.api_public_listings.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  # Exact path match only (no wildcard) - POST /api/temporal-homes/request is
  # authenticated and shares this prefix. A wildcard would also force that
  # route's allowed_methods down to GET/HEAD/OPTIONS for the whole prefix,
  # which would make CloudFront reject the POST outright.
  ordered_cache_behavior {
    path_pattern             = "/api/temporal-homes"
    target_origin_id         = "ecs-task"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = aws_cloudfront_cache_policy.api_public_listings.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  # Catch-all for every other /api/* path (auth, users, admin, image uploads, etc.) not covered by
  # a more specific behavior above - MUST be the last ordered_cache_behavior (CloudFront evaluates
  # these in the order they're listed here, first match wins), or it would shadow the specific
  # listing-endpoint behaviors above it. Same settings the old default_cache_behavior used before
  # the static site became the default (see the comment on default_cache_behavior above) - full
  # method set, CachingDisabled, and the viewer-country-forwarding origin request policy that GET
  # /api/detect-country needs.
  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "ecs-task"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = aws_cloudfront_origin_request_policy.all_viewer_plus_country.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = data.aws_acm_certificate.wildcard_us_east_1.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}
