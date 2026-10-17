import type { Metadata } from "next";
import { Suspense } from "react";

import { LegacyTaskRedirect } from "@/components/chat/LegacyTaskRedirect";

export const metadata: Metadata = {
  title: "Opening your search | Hotel Deal Agent",
};

/** Old links (`/task?id=<taskId>`) open the same search in the chat (`/?task=<taskId>`). */
export default function TaskPage() {
  return (
    <main className="flex h-dvh items-center justify-center text-sm text-muted">
      <Suspense fallback={<p role="status">Opening your search…</p>}>
        <LegacyTaskRedirect />
      </Suspense>
    </main>
  );
}
