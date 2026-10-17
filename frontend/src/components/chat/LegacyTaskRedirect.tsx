"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect } from "react";

/** Sends `/task?id=<id>` to `/?task=<id>`, where the chat restores the search. */
export function LegacyTaskRedirect() {
  const router = useRouter();
  const id = useSearchParams().get("id")?.trim();

  useEffect(() => {
    router.replace(id ? `/?task=${encodeURIComponent(id)}` : "/");
  }, [id, router]);

  return <p role="status">Opening your search…</p>;
}
