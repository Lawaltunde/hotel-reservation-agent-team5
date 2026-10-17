"use client";

import dynamic from "next/dynamic";

/**
 * The search form's defaults (dates, deadline) come from the user's clock, so it must not be
 * prerendered at build time: the static export would bake in the build date and hydration
 * would mismatch. Load it on the client only.
 */
export const ClientSearchForm = dynamic(() => import("@/components/SearchForm").then((m) => m.SearchForm), {
  ssr: false,
  loading: () => (
    <div aria-busy="true" className="flex flex-col gap-4" role="status">
      <span className="sr-only">Loading the search form…</span>
      {[0, 1, 2].map((i) => (
        <div key={i} className="h-16 animate-pulse rounded-lg bg-slate-100" />
      ))}
    </div>
  ),
});
