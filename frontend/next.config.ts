import { PHASE_DEVELOPMENT_SERVER } from "next/constants";
import type { NextConfig } from "next";

// Production: a static export (out/) that the frontend Maven module packages into the
// Spring Boot jar, so the UI and /api are served from the same origin by one process.
// Development (`npm run dev`): /api/* is proxied to the backend for hot reload on :3000.
const backendUrl = (process.env.BACKEND_URL ?? "http://localhost:8080").replace(/\/+$/, "");

export default function nextConfig(phase: string): NextConfig {
  if (phase === PHASE_DEVELOPMENT_SERVER) {
    return {
      // The dev server gzips proxied responses, which buffers the SSE progress stream
      // (/api/tasks/{id}/events) until it ends. Compression is pointless on localhost anyway.
      compress: false,
      async rewrites() {
        return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
      },
    };
  }
  return { output: "export" };
}
