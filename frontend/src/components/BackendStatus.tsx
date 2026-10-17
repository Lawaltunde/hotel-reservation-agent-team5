"use client";

import { useEffect, useState } from "react";

import type { HealthStatus } from "@/models/health";
import { getHealth } from "@/services/api-client";

const POLL_INTERVAL_MS = 15000;

type StatusState =
  | { kind: "checking" }
  | { kind: "online"; health: HealthStatus }
  | { kind: "offline"; reason: string };

/** Small indicator that polls `GET /api/health` and shows whether the backend is reachable. */
export function BackendStatus() {
  const [state, setState] = useState<StatusState>({ kind: "checking" });

  useEffect(() => {
    const controller = new AbortController();

    async function check() {
      try {
        const health = await getHealth({ signal: controller.signal });
        if (controller.signal.aborted) return;
        setState(
          health.status === "UP"
            ? { kind: "online", health }
            : { kind: "offline", reason: `Backend reported status ${health.status}` },
        );
      } catch (error) {
        if (controller.signal.aborted) return;
        setState({
          kind: "offline",
          reason: error instanceof Error ? error.message : "Unknown error",
        });
      }
    }

    check();
    const timer = setInterval(check, POLL_INTERVAL_MS);
    return () => {
      controller.abort();
      clearInterval(timer);
    };
  }, []);

  const { label, dotClass, title } = describe(state);

  return (
    <div
      role="status"
      aria-live="polite"
      title={title}
      className="inline-flex items-center gap-2 font-mono text-[0.7rem] tracking-[0.1em] text-muted uppercase"
    >
      <span aria-hidden="true" className={`h-2.5 w-2.5 rounded-full ${dotClass}`} />
      <span className="sr-only sm:not-sr-only">Agent {label}</span>
    </div>
  );
}

function describe(state: StatusState): { label: string; dotClass: string; title: string } {
  switch (state.kind) {
    case "checking":
      return { label: "checking…", dotClass: "bg-line motion-safe:animate-pulse", title: "Contacting backend" };
    case "online":
      return {
        label: "online",
        dotClass: "bg-pine",
        title: `${state.health.service} is up (server time ${state.health.time})`,
      };
    case "offline":
      return { label: "offline", dotClass: "bg-alarm", title: state.reason };
  }
}
