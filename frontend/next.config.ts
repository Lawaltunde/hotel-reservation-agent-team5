import type { NextConfig } from "next";

// Where the Spring Boot backend listens. The browser only talks to this Next.js
// server; requests to /api/* are proxied to the backend, so there is no CORS
// setup and no backend URL baked into client bundles.
const backendUrl = (process.env.BACKEND_URL ?? "http://localhost:8080").replace(
  /\/+$/,
  "",
);

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
