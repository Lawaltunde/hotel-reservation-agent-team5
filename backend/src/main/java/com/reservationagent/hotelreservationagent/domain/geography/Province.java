package com.reservationagent.hotelreservationagent.domain.geography;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Canadian provinces and territories: the time zone used for "today" and dates, and the combined
 * sales tax on short-term accommodation (GST/HST/PST/QST) used for a tier-B price estimate.
 * Municipal or tourism accommodation levies are not included; they are never guessed.
 */
public enum Province {
    ON("Ontario", "America/Toronto", "0.13"),
    QC("Quebec", "America/Montreal", "0.14975"),
    NS("Nova Scotia", "America/Halifax", "0.14"),
    NB("New Brunswick", "America/Halifax", "0.15"),
    MB("Manitoba", "America/Winnipeg", "0.12"),
    BC("British Columbia", "America/Vancouver", "0.13"),
    PE("Prince Edward Island", "America/Halifax", "0.15"),
    SK("Saskatchewan", "America/Regina", "0.11"),
    AB("Alberta", "America/Edmonton", "0.05"),
    NL("Newfoundland and Labrador", "America/St_Johns", "0.15"),
    YT("Yukon", "America/Whitehorse", "0.05"),
    NT("Northwest Territories", "America/Yellowknife", "0.05"),
    NU("Nunavut", "America/Iqaluit", "0.05");

    private final String displayName;
    private final ZoneId zone;
    private final BigDecimal salesTaxRate;

    Province(String displayName, String zone, String salesTaxRate) {
        this.displayName = displayName;
        this.zone = ZoneId.of(zone);
        this.salesTaxRate = new BigDecimal(salesTaxRate);
    }

    public String displayName() {
        return displayName;
    }

    public ZoneId zone() {
        return zone;
    }

    /** Combined GST/HST/PST/QST on accommodation, as a fraction (e.g. 0.13). */
    public BigDecimal salesTaxRate() {
        return salesTaxRate;
    }

    /** "NS", "ns", "Nova Scotia" or "nova scotia" (also "Québec"); empty for anything else. */
    public static Optional<Province> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String v = java.text.Normalizer.normalize(value.strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace('.', ' ').trim();
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(v) || p.displayName.toLowerCase(Locale.ROOT).equals(v)
                        || (p == PE && v.equals("pei")) || (p == NL && v.equals("newfoundland")))
                .findFirst();
    }
}
