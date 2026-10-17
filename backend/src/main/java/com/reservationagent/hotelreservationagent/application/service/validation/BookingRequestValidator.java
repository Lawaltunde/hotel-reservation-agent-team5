package com.reservationagent.hotelreservationagent.application.service.validation;

import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase.Command;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityGeocoder;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityRepository;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException.FieldError;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.geography.Province;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Hard validation rules. Deterministic; no LLM involvement. Destinations anywhere in Canada. */
@Component
public class BookingRequestValidator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final MunicipalityRepository municipalities;
    private final MunicipalityGeocoder geocoder;
    private final Clock clock;

    public BookingRequestValidator(MunicipalityRepository municipalities, MunicipalityGeocoder geocoder, Clock clock) {
        this.municipalities = municipalities;
        this.geocoder = geocoder;
        this.clock = clock;
    }

    /** @return the municipality the request resolves to */
    public Municipality validate(Command command) {
        List<FieldError> errors = new ArrayList<>();
        // The westernmost "today" in Canada: the destination's zone is not known yet, and a date that is
        // still today somewhere in Canada must not be rejected as past.
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("America/Vancouver")));

        if (isBlank(command.requestId())) errors.add(new FieldError("requestId", "Request ID is required"));
        if (isBlank(command.city())) errors.add(new FieldError("city", "City is required"));
        if (command.checkIn() == null) errors.add(new FieldError("checkIn", "Check-in date is required"));
        if (command.checkOut() == null) errors.add(new FieldError("checkOut", "Check-out date is required"));
        if (command.checkIn() != null && command.checkIn().isBefore(today)) {
            errors.add(new FieldError("checkIn", "Check-in date is in the past"));
        }
        if (command.checkIn() != null && command.checkOut() != null && !command.checkOut().isAfter(command.checkIn())) {
            errors.add(new FieldError("checkOut", "Check-out must be after check-in"));
        }
        if (command.rooms() < 1) errors.add(new FieldError("rooms", "At least 1 room is required"));
        if (command.adults() < 1) errors.add(new FieldError("adults", "At least 1 adult is required"));
        if (command.children() < 0) errors.add(new FieldError("children", "Children cannot be negative"));
        if (isBlank(command.destinationEmail()) || !EMAIL.matcher(command.destinationEmail()).matches()) {
            errors.add(new FieldError("destinationEmail", "A valid email address is required"));
        }
        if (command.deadline() == null || !command.deadline().isAfter(clock.instant())) {
            errors.add(new FieldError("deadline", "Deadline must be in the future"));
        }
        if (command.maximumTotalCad() != null && command.maximumTotalCad().signum() <= 0) {
            errors.add(new FieldError("maximumTotalCad", "Budget must be positive"));
        }
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
        return resolveMunicipality(command.city(),
                command.intake() == null ? null : Province.parse(command.intake().provinceHint()).orElse(null));
    }

    /**
     * Finds the Canadian municipality the guest means. With a province other than Ontario the seeded
     * (Ontario) table is skipped, so "Windsor, Nova Scotia" never becomes Windsor ON. Otherwise the
     * table is tried first, then the geocoder.
     *
     * @param provinceHint the province the guest named, or {@code null}
     */
    public Municipality resolveMunicipality(String city, Province provinceHint) {
        String name = city.trim();
        if (provinceHint == null || provinceHint == Province.ON) {
            List<Municipality> matches = municipalities.findByNameOrAlias(name).stream()
                    .filter(m -> provinceHint == null || m.provinceOrOntario() == provinceHint)
                    .toList();
            if (matches.size() == 1) {
                return matches.getFirst();
            }
            if (matches.size() > 1) {
                // Same name in several provinces and none named: an Ontario match is the seeded, major one.
                List<Municipality> ontario = matches.stream().filter(m -> m.provinceOrOntario() == Province.ON).toList();
                if (ontario.size() == 1) {
                    return ontario.getFirst();
                }
                throw new OutOfScopeLocationException("'" + city + "' matches several Canadian municipalities",
                        matches.stream().map(m -> m.name() + ", " + m.provinceOrOntario().name()).toList());
            }
        }
        // Not in the table (or another province): a smaller town, a typo, or elsewhere in Canada.
        Optional<MunicipalityGeocoder.Locality> locality = geocoder.findCanadianLocality(name, provinceHint);
        if (locality.isPresent()) {
            return municipalities.addGeocoded(locality.get().name(), locality.get().centre(), locality.get().province());
        }
        String prefix = name.length() >= 3 ? name.substring(0, 3) : name;
        throw new OutOfScopeLocationException("'" + city + "' is not a Canadian city we could find",
                municipalities.searchByPrefix(prefix, 5).stream().map(Municipality::name).toList());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
