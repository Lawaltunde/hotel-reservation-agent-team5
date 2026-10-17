import type { Metadata } from "next";
import { Suspense } from "react";

import { TaskFromSearchParams } from "@/components/TaskView";

export const metadata: Metadata = {
  title: "Search progress | Hotel Deal Agent",
};

// /task?id=<taskId>. A query parameter, not a dynamic segment, because the app is a static export.
export default function TaskPage() {
  return (
    <main className="mx-auto max-w-3xl px-4 py-10 sm:px-6 sm:py-14">
      <Suspense
        fallback={
          <div role="status" className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-600">
            Loading your search…
          </div>
        }
      >
        <TaskFromSearchParams />
      </Suspense>
    </main>
  );
}
