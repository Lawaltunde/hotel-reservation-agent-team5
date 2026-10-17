# Hotel Deal Agent: frontend

Next.js (App Router) + TypeScript + Tailwind CSS UI for the Hotel Deal Agent. Users pick an
Ontario city, dates and guests; the backend phones hotels and negotiates; this app shows live
progress and the final result. See the root `README.md` (§11) for the request contract.

## Requirements

- Node.js 20.9 or newer (Node 22/24 LTS recommended)
- The Spring Boot backend (`../hotel-reservation-agent`) running, for anything beyond the
  static page. Without it the page still loads and shows "Backend: offline".

## Run

```bash
cd frontend
npm ci              # install exact versions from package-lock.json
npm run dev         # http://localhost:3000, hot reload
```

Production build:

```bash
npm run build
npm run start       # serves the build on http://localhost:3000
```

Other scripts:

| Script              | What it does              |
| ------------------- | ------------------------- |
| `npm run lint`      | ESLint (Next.js rules)    |
| `npm run typecheck` | `tsc --noEmit`            |

## Configuration

| Variable      | Default                 | Purpose                                   |
| ------------- | ----------------------- | ----------------------------------------- |
| `BACKEND_URL` | `http://localhost:8080` | Base URL of the backend that `/api/*` is proxied to. |

Copy `.env.example` to `.env.local` to override it, or set it in the shell.

The browser only talks to the Next.js server. `next.config.ts` rewrites `/api/:path*` to
`${BACKEND_URL}/api/:path*`, so there is no CORS setup and no backend URL in client code.

**Note:** rewrites are resolved when the app is built. For `npm run build` + `npm run start`,
set `BACKEND_URL` at **build** time; changing it only for `npm run start` has no effect.
`npm run dev` picks it up on each start.

If the backend is down, `/api/*` requests return HTTP 500 from Next.js (proxy connection
refused), and the UI shows the backend as offline.

## Layout

```
src/
  app/          routes and layouts (presentation)
  components/   UI components (presentation)
  models/       TypeScript types for API data
  services/     API client code (typed fetch wrapper)
```
