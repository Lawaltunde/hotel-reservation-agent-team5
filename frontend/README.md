# Hotel Deal Agent: frontend

Next.js (App Router) + TypeScript + Tailwind CSS UI for the Hotel Deal Agent. Users chat with the
agent in one message (an Ontario city, dates, guests and their email); the backend phones hotels and negotiates;
this app shows live progress, the quotes and the email that was sent. See the root `README.md` (§11) for the request contract.

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
| `/` | The chat with the agent. Each user message resends the whole conversation (all user messages joined by newlines; the latest email address wins) to `POST /api/tasks/prompt` with an `Idempotency-Key`. A 400 becomes a follow-up question for only what's missing; a 422 offers "Search <city>" chips for an ambiguous Ontario city. On 202 the URL becomes `/?task=<id>` |
| `/?task=<id>` | The same chat, rebuilt from `GET /api/tasks/{id}` (so a refresh works): what was understood (`task.request`), live progress over SSE (polling every 3 s as a fallback), the result, the quotes, and the email that was sent (`task.report`) |
| `/task?id=<id>` | Old link format; redirects to `/?task=<id>` |

## Code layout

```
src/
  app/              routes and layouts only
  components/chat/  the chat: ChatApp (layout), useAgentChat (state), intake (pure prompt and follow-up
                    logic), ChatLog, Composer, Messages, TaskThread, UnderstoodCard, EmailPreview
  components/       shared result UI (OfferCard, ResultSummary, BackendStatus, format)
  models/           API types (task, problem-detail, health)
  services/         API client: apiRequest/ApiError, createTaskFromPrompt, getTask, subscribeToTask
```

**Email preview.** `report.bodyHtml` is rendered only in an `<iframe sandbox="">` via `srcdoc`, with a CSP
that blocks all network requests (no remote images or tracking pixels). It is never injected into the
page. Only `http(s)` recording URLs are linked.

**Design.** Colours are CSS custom properties in `globals.css` (light values, then dark values under
`prefers-color-scheme: dark`) exposed to Tailwind as `bg-paper`, `text-ink`, `text-pine` and so on;
use those rather than Tailwind's palette so dark mode keeps working. Fonts (Young Serif, Atkinson
Hyperlegible Next and Mono) are self-hosted at build time by `next/font`.

**Honesty rule (root README §1).** The UI never presents a quote or enquiry as a booking. An
enquiry ID is labelled "Hotel reference (not a booking)", unknown amounts read "Unknown" (never 0),
and tax status is shown as included, not included or unknown. Only the outcome
`BOOKING_CONFIRMED` is shown as booked.
