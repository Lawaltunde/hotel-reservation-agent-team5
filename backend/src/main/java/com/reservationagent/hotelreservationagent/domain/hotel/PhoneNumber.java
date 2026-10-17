package com.reservationagent.hotelreservationagent.domain.hotel;

import com.reservationagent.hotelreservationagent.domain.shared.DomainException;

import java.util.Set;
import java.util.regex.Pattern;

/** A North American number in E.164 form, e.g. {@code +16135550100}. */
public record PhoneNumber(String e164) {

    private static final Pattern NANP_E164 = Pattern.compile("^\\+1[2-9]\\d{2}[2-9]\\d{6}$");
    private static final Set<String> TOLL_FREE = Set.of("800", "833", "844", "855", "866", "877", "888");

    public PhoneNumber {
        if (e164 == null || !NANP_E164.matcher(e164).matches()) {
            throw new DomainException("Not a NANP E.164 number: " + e164);
        }
    }

    public String areaCode() {
        return e164.substring(2, 5);
    }

    /** Toll-free numbers usually reach central reservations rather than the front desk. */
    public boolean isTollFree() {
        return TOLL_FREE.contains(areaCode());
    }
}
