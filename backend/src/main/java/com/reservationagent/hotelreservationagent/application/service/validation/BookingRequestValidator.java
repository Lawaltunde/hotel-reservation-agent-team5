package com.reservationagent.hotelreservationagent.application.service.validation;

import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase.Command;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityGeocoder;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityRepository;
import com.reservationagent.hotelreservationagent.application.service.validation.RequestValidationException.FieldError;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Hard validation rules. Deterministic; no LLM involvement. "Today" is the date in Ontario, not UTC. */
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

    /** @return the Ontario municipality the request resolves to */
    public Municipality validate(Command command) {
        List<FieldError> errors = new ArrayList<>();
        LocalDate today = LocalDate.now(clock.withZone(Municipality.DEFAULT_TIME_ZONE));

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
        return resolveOntarioMunicipality(command.city());
    }

    private Municipality resolveOntarioMunicipality(String city) {
        List<Municipality> matches = municipalities.findByNameOrAlias(city.trim());
        if (matches.isEmpty()) {
            // Not in the table: a smaller town or a typo. Ask the geocoder before giving up.
            Optional<MunicipalityGeocoder.Locality> locality = geocoder.findOntarioLocality(city.trim());
            if (locality.isPresent()) {
                return municipalities.addGeocoded(locality.get().name(), locality.get().centre());
            }
            String prefix = city.trim().length() >= 3 ? city.trim().substring(0, 3) : city.trim();
            throw new OutOfScopeLocationException("'" + city + "' is not a supported Ontario municipality",
                    municipalities.searchByPrefix(prefix, 5).stream().map(Municipality::name).toList());
        }
        if (matches.size() > 1) {
            throw new OutOfScopeLocationException(
                    "'" + city + "' matches several Ontario municipalities", matches.stream().map(Municipality::name).toList());
        }
        return matches.getFirst();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
