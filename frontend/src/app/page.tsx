import { Suspense } from "react";

import { ChatApp } from "@/components/chat/ChatApp";

// The chat reads `?task=<id>` (static export: a query parameter, not a route), which needs Suspense.
export default function HomePage() {
  return (
    <Suspense
      fallback={
        <div role="status" className="flex h-dvh items-center justify-center text-sm text-muted">
          Loading the agent…
        </div>
      }
    >
      <ChatApp />
    </Suspense>
  );
}
