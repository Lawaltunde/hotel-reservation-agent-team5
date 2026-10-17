# Hotel Deal Agent: frontend

Next.js (App Router) + TypeScript + Tailwind CSS UI for the Hotel Deal Agent. Users pick an
Ontario city, dates and guests; the backend phones hotels and negotiates; this app shows live
progress and the final result. See the root `README.md` (§11) for the request contract.

## How it ships

Production builds are a **static export** (`output: "export"` in `next.config.ts`, written to
`out/`). The `frontend/pom.xml` Maven module runs `npm ci` and `npm run build` (with a Node it
downloads itself) and packages `out/` under `static/`; the Spring Boot backend serves it at `/`.
There is no Node server in production. That means no server-side rendering, server actions or
route handlers: everything renders in the browser and talks to `/api/**`. For pages with IDs, use
query parameters (`/task?id=...`), not dynamic segments.

## Develop (hot reload)

```bash
# backend running on :8080 (see root README §19)
cd frontend
npm ci
npm run dev         # http://localhost:3000, proxies /api/* to BACKEND_URL (default http://localhost:8080)
```

| Script | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload and the `/api` proxy |
| `npm run build` | Static export to `out/` |
| `npm run lint` | ESLint |
| `npm run typecheck` | `tsc --noEmit` |

`BACKEND_URL` (see `.env.example`) applies to `npm run dev` only.
