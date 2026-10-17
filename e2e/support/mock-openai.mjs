// Stand-in for the OpenAI Chat Completions API, for e2e runs of the chat intake.
// It answers the prompt reader (TaskPromptInterpreter) with a fixed reading chosen by keyword, so the
// chat flows are deterministic and no real model is called. Start it before the app and point the
// app at it: SPRING_AI_OPENAI_BASE_URL=http://localhost:4010/v1 (see e2e/README.md).
//
//   "explode" in the request  -> HTTP 500 (the app answers 503: request reader unavailable)
//   "vancouver"               -> city Vancouver (the app answers 422: outside Ontario)
//   "ottawa"                  -> Ottawa, tomorrow, 2 nights, 2 adults, up to $250 a night
//   anything else             -> nothing understood (every field null)
import { createServer } from "node:http";

const port = Number(process.env.MOCK_OPENAI_PORT ?? 4010);
let calls = 0;

const date = (kind) => ({ kind, year: null, month: null, day: null, weekday: null, days: null });

function reading(text) {
  const base = {
    city: null, checkIn: null, checkOut: null, nights: null, rooms: null, adults: null, children: null,
    budget: null, amenities: [], notes: null,
  };
  if (text.includes("vancouver")) return { ...base, city: "Vancouver", checkIn: date("TOMORROW"), nights: 2, adults: 2 };
  if (text.includes("ottawa")) {
    return { ...base, city: "Ottawa", checkIn: date("TOMORROW"), nights: 2, adults: 2,
      budget: { amount: 250, unit: "PER_NIGHT" } };
  }
  return base;
}

createServer((req, res) => {
  if (req.method === "GET" && req.url === "/__calls") {
    res.writeHead(200, { "Content-Type": "application/json" }).end(JSON.stringify({ calls }));
    return;
  }
  if (req.method !== "POST" || !req.url.endsWith("/chat/completions")) {
    res.writeHead(404).end();
    return;
  }
  let body = "";
  req.on("data", (chunk) => (body += chunk));
  req.on("end", () => {
    calls++;
    let messages = [];
    try {
      messages = JSON.parse(body).messages ?? [];
    } catch {
      // fall through with no messages
    }
    const user = messages.filter((m) => m.role === "user").map((m) =>
      typeof m.content === "string" ? m.content : JSON.stringify(m.content)).join("\n").toLowerCase();
    if (user.includes("explode")) {
      res.writeHead(500, { "Content-Type": "application/json" })
        .end(JSON.stringify({ error: { message: "mock outage", type: "server_error" } }));
      return;
    }
    res.writeHead(200, { "Content-Type": "application/json" }).end(JSON.stringify({
      id: `chatcmpl-e2e-${calls}`,
      object: "chat.completion",
      created: Math.floor(Date.now() / 1000),
      model: "gpt-5.4-mini",
      choices: [{ index: 0, message: { role: "assistant", content: JSON.stringify(reading(user)) }, finish_reason: "stop" }],
      usage: { prompt_tokens: 300, completion_tokens: 60, total_tokens: 360 },
    }));
  });
}).listen(port, () => console.log(`mock OpenAI listening on http://localhost:${port}`));
