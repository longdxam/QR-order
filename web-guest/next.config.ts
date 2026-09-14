import type { NextConfig } from "next";

// Chưa chốt Nginx hay Spring Cloud Gateway (OPEN-01), nên chưa có hạ tầng chung để web-guest và
// backend cùng origin. Next.js tự proxy /api/** sang backend — né vấn đề CORS mà không cần quyết
// định OPEN-01 trước, và cách này vẫn hoạt động y hệt nếu sau này Next.js được deploy đứng trước
// backend thật.
const BACKEND_URL = process.env.QROS_BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // Root repo cũng có package-lock.json riêng (Spectral/openapi-typescript) — chỉ rõ root của
  // chính app này để Next.js không tự đoán nhầm workspace root.
  turbopack: {
    root: import.meta.dirname,
  },
  // Next.js 16 tự sinh AGENTS.md/CLAUDE.md riêng cho web-guest — đụng độ với CLAUDE.md thật ở gốc
  // repo (nguồn sự thật duy nhất của dự án). Tắt hẳn, không dùng bản Next.js tự sinh.
  agentRules: false,
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${BACKEND_URL}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
