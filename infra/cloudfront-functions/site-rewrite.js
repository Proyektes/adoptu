// Viewer-request rewrite for the adoptu static site (S3 origin behind aws_cloudfront_distribution
// "app" - see infra/cloudfront.tf). Same rewrite table as scripts/serve_site.py/serve.json
// (written by frontend/src/jvmMain/kotlin/com/adoptu/site/SiteGenerator.kt) - keep both in sync:
// the two dynamic path-param pages (/pet/{id}, /temporal-home/{id}), then clean-URL resolution
// (append .html to any extensionless path) for everything else.
function handler(event) {
    var request = event.request;
    var uri = request.uri;

    if (/^\/pet\/[0-9]+$/.test(uri)) {
        request.uri = "/pet-detail.html";
        return request;
    }
    if (/^\/temporal-home\/[0-9]+$/.test(uri)) {
        request.uri = "/temporal-home-detail.html";
        return request;
    }
    if (uri === "/") {
        request.uri = "/index.html";
        return request;
    }

    var lastSegment = uri.substring(uri.lastIndexOf("/") + 1);
    if (lastSegment.indexOf(".") === -1) {
        request.uri = uri + ".html";
    }
    return request;
}
