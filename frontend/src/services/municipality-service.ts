import type { Municipality } from "@/models/municipality";
import { apiRequest, type RequestOptions } from "@/services/api-client";

/** `GET /api/municipalities?q=&limit=`: Ontario municipalities matching a prefix. */
export function searchMunicipalities(
  query: string,
  limit = 10,
  options?: RequestOptions,
): Promise<Municipality[]> {
  const params = new URLSearchParams({ q: query, limit: String(limit) });
  return apiRequest<Municipality[]>(`/municipalities?${params}`, {
    ...options,
    method: "GET",
  }).then((list) => (Array.isArray(list) ? list : []));
}
