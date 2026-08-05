import cf from 'cloudfront';

// Viewer-request rewrite for the adoptu static site (S3 origin behind aws_cloudfront_distribution
// "app" - see infra/cloudfront.tf). Same rewrite table as scripts/serve_site.py/serve.json
// (written by frontend/src/jvmMain/kotlin/com/adoptu/site/SiteGenerator.kt) - keep both in sync:
// the two dynamic path-param pages (/pet/{id}, /temporal-home/{id}), then clean-URL resolution
// (append .html to any extensionless path) for everything else.

// Social-preview crawlers (WhatsApp, Facebook, Slack, etc.) never execute the client-side JS that
// fills in pet-detail.html with real data, so they'd otherwise see an empty shell. Route just these
// user agents to the backend's server-rendered og:image/title endpoint (PetsRoutes.kt's
// GET /api/share/pet/{id}) instead - real visitors are untouched, still get /pet-detail.html below.
var CRAWLER_UA_RE = /facebookexternalhit|Facebot|Twitterbot|WhatsApp|LinkedInBot|Slackbot|TelegramBot|Discordbot|SkypeUriPreview|Pinterest|redditbot|Applebot/i;

function handler(event) {
    var request = event.request;
    var uri = request.uri;

    var petMatch = /^\/pet\/([0-9]+)$/.exec(uri);
    if (petMatch) {
        var ua = request.headers["user-agent"] ? request.headers["user-agent"].value : "";
        if (CRAWLER_UA_RE.test(ua)) {
            cf.selectRequestOriginById("ecs-task");
            request.uri = "/api/share/pet/" + petMatch[1];
            return request;
        }
        request.uri = "/pet-detail.html";
        return request;
    }
    if (/^\/temporal-home\/[0-9]+$/.test(uri)) {
        request.uri = "/temporal-home-detail.html";
        return request;
    }
    if (/^\/lost-found\/[0-9]+$/.test(uri)) {
        request.uri = "/lost-found-detail.html";
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
