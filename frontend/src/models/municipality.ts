/** An Ontario municipality from `GET /api/municipalities`. */
export interface Municipality {
  id: string | number;
  name: string;
  /** IANA time zone, e.g. "America/Toronto". */
  timeZone: string;
}

/** Ontario's main time zone; the north-west uses America/Winnipeg. */
export const DEFAULT_ONTARIO_TIME_ZONE = "America/Toronto";
