// Post-build Subresource Integrity (SHA-512) for the Face Angular liveness sample.
//
// Angular's built-in `subresourceIntegrity` build option emits sha384 and the
// algorithm is hardcoded in @angular/build (not configurable). To stay consistent
// with the rest of the Face liveness SRI work (Vision Studio + liveness-webapp use
// sha512), that option is disabled (`subresourceIntegrity: false` in angular.json)
// and this post-build step stamps sha512 `integrity` + `crossorigin="anonymous"`
// onto the built index.html's local <script>/<link> tags, hashing the actual
// emitted bundle bytes. No third-party dependencies (Node built-ins only).
import { createHash } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const sampleRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const browserDir = join(sampleRoot, "dist", "face-angular-js", "browser");
const indexPath = join(browserDir, "index.html");

if (!existsSync(indexPath)) {
    console.error(`[sri] build output not found: ${indexPath} — run \`ng build\` first.`);
    process.exit(1);
}

// Only same-origin (local, non-data:) URLs get SRI; external URLs are left alone.
const isLocal = (url) => !!url && !/^(?:https?:)?\/\//i.test(url) && !url.startsWith("data:");

function sha512Of(url) {
    const file = join(browserDir, url.split(/[?#]/)[0].replace(/^\//, ""));
    if (!existsSync(file)) {
        console.error(`[sri] referenced asset is missing on disk: ${url} -> ${file}`);
        process.exit(1);
    }
    return "sha512-" + createHash("sha512").update(readFileSync(file)).digest("base64");
}

let html = readFileSync(indexPath, "utf8");
let stamped = 0;

// Insert integrity + crossorigin into an opening tag (before its closing '>'),
// unless it already has an integrity or points at an external URL.
function stamp(tag, url) {
    if (/\sintegrity=/i.test(tag) || !isLocal(url)) {
        return tag;
    }
    stamped++;
    return tag.replace(/\/?>$/, ` integrity="${sha512Of(url)}" crossorigin="anonymous"$&`);
}

// <script ... src="..."> — the emitted module/classic bundles.
html = html.replace(/<script\b[^>]*>/gi, (tag) => {
    const m = tag.match(/\bsrc="([^"]+)"/i);
    return m ? stamp(tag, m[1]) : tag;
});

// <link rel="stylesheet|modulepreload" href="..."> — styles + preloaded chunks.
html = html.replace(/<link\b[^>]*>/gi, (tag) => {
    if (!/\brel="(?:stylesheet|modulepreload)"/i.test(tag)) {
        return tag;
    }
    const m = tag.match(/\bhref="([^"]+)"/i);
    return m ? stamp(tag, m[1]) : tag;
});

writeFileSync(indexPath, html);
console.log(`[sri] stamped sha512 integrity on ${stamped} tag(s) in dist/face-angular-js/browser/index.html`);

if (stamped === 0) {
    console.error("[sri] no local <script>/<link> tags were found to stamp — check the build output.");
    process.exit(1);
}
