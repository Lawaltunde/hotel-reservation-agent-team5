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

`BACKEND_URL` (see `.env.example`) applies to `npm run dev` only. The dev server has gzip turned
off (`compress: false`) because it would otherwise buffer the SSE progress stream.

## Pages

| Route | What it shows |
|---|---|
| `/` | Search form: Ontario city autocomplete, dates, guests, email, deadline, budget, authorization mode |
| `/task?id=<taskId>` | Live progress (SSE, falling back to polling every 3 s) and the result with every quote |

## Code layout

```
src/
  app/          routes and layouts only
  components/   UI (SearchForm, CityAutocomplete, ProgressTimeline, OfferCard, ResultSummary, TaskView)
  models/       API types (task, municipality, problem-detail, health) and the zod form schema (search-form)
  services/     API client: apiRequest/ApiError, createTask, getTask, subscribeToTask, searchMunicipalities
```

**Honesty rule (root README §1).** The UI never presents a quote or enquiry as a booking. An
enquiry ID is labelled "Hotel reference (not a booking)", unknown amounts read "Unknown" (never 0),
and tax status is shown as included, not included or unknown. Only the outcome
`BOOKING_CONFIRMED` is shown as booked.
