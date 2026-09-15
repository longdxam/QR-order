import type { NextConfig } from "next";

const BACKEND_URL = process.env.QROS_BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  turbopack: { root: import.meta.dirname },
  agentRules: false,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${BACKEND_URL}/api/:path*` }];
  },
};

export default nextConfig;
