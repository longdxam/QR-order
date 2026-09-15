import { gzipSync } from "node:zlib";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import vm from "node:vm";

const appRoot = process.cwd();
const buildRoot = path.join(appRoot, ".next");
const budgetBytes = Number.parseInt(process.env.QROS_INITIAL_JS_BUDGET_KB ?? "180", 10) * 1024;

const routes = [
  ["/ma-ban", ["ma-ban"]],
  ["/menu", ["(session)", "menu"]],
  ["/cart", ["(session)", "cart"]],
  ["/orders/[orderId]", ["(session)", "orders", "[orderId]"]],
  ["/t/[qrToken]", ["t", "[qrToken]"]],
];

if (!Number.isFinite(budgetBytes) || budgetBytes <= 0) {
  throw new Error("QROS_INITIAL_JS_BUDGET_KB phải là số nguyên dương.");
}

const buildManifestPath = path.join(buildRoot, "build-manifest.json");
if (!existsSync(buildManifestPath)) {
  throw new Error("Không thấy .next/build-manifest.json. Hãy chạy npm run build trước.");
}
const buildManifest = JSON.parse(readFileSync(buildManifestPath, "utf8"));
const rootFiles = buildManifest.rootMainFiles ?? [];

let exceeded = false;
for (const [route, segmentPath] of routes) {
  const manifestPath = path.join(
    buildRoot,
    "server",
    "app",
    ...segmentPath,
    "page_client-reference-manifest.js",
  );
  if (!existsSync(manifestPath)) {
    throw new Error("Không thấy client-reference manifest cho " + route + ": " + manifestPath);
  }

  const manifest = loadClientManifest(manifestPath);
  const entryFiles = Object.values(manifest.entryJSFiles ?? {}).flat();
  const chunks = [...new Set([...rootFiles, ...entryFiles])];
  const sizes = chunks.map((chunk) => [chunk, gzipSize(chunk)]);
  const total = sizes.reduce((sum, [, size]) => sum + size, 0);
  const state = total <= budgetBytes ? "PASS" : "FAIL";

  console.log(state + " " + route + ": " + formatBytes(total) + " / " + formatBytes(budgetBytes));
  for (const [chunk, size] of sizes) {
    console.log("  " + formatBytes(size).padStart(9) + "  " + chunk);
  }
  exceeded ||= total > budgetBytes;
}

if (exceeded) {
  process.exitCode = 1;
  console.error(
    "NFR-PERF-07 thất bại: initial JavaScript gzip vượt " + formatBytes(budgetBytes) + ". "
      + "Tách/lazy-load code trước khi tăng ngân sách.",
  );
}

function loadClientManifest(manifestPath) {
  const context = {};
  vm.runInNewContext(readFileSync(manifestPath, "utf8"), context, { filename: manifestPath });
  const manifests = context.__RSC_MANIFEST;
  const manifest = manifests && Object.values(manifests)[0];
  if (!manifest) {
    throw new Error("Client-reference manifest không hợp lệ: " + manifestPath);
  }
  return manifest;
}

function gzipSize(relativePath) {
  const filePath = path.join(buildRoot, relativePath.replace(/^\/_next\//, ""));
  if (!existsSync(filePath)) {
    throw new Error("Chunk đã khai báo nhưng không tồn tại: " + relativePath);
  }
  return gzipSync(readFileSync(filePath), { level: 9 }).length;
}

function formatBytes(bytes) {
  return (bytes / 1024).toFixed(1) + " KB";
}
