package com.reservationagent.hotelreservationagent.application.service.validation;

import com.reservationagent.hotelreservationagent.application.port.in.SubmitBookingTaskUseCase.Command;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityRepository;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Hard validation rules. Deterministic; no LLM involvement. */
@Component
public class BookingRequestValidator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final MunicipalityRepository municipalities;
    private final Clock clock;

    public BookingRequestValidator(MunicipalityRepository municipalities, Clock clock) {
        this.municipalities = municipalities;
        this.clock = clock;
    }

    /** @return the Ontario municipality the request resolves to */
    public Municipality validate(Command command) {
        List<String> errors = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);

        if (isBlank(command.requestId())) errors.add("requestId is required");
        if (isBlank(command.city())) errors.add("city is required");
        if (command.checkIn() == null) errors.add("checkIn is required");
        if (command.checkOut() == null) errors.add("checkOut is required");
        if (command.checkIn() != null && command.checkIn().isBefore(today)) errors.add("checkIn is in the past");
        if (command.checkIn() != null && command.checkOut() != null && !command.checkOut().isAfter(command.checkIn())) {
            errors.add("checkOut must be after checkIn");
        }
        if (command.rooms() < 1) errors.add("rooms must be at least 1");
        if (command.adults() < 1) errors.add("adults must be at least 1");
        if (command.children() < 0) errors.add("children cannot be negative");
        if (isBlank(command.destinationEmail()) || !EMAIL.matcher(command.destinationEmail()).matches()) {
            errors.add("destinationEmail is invalid");
        }
        if (command.deadline() == null || !command.deadline().isAfter(clock.instant())) {
            errors.add("deadline must be in the future");
        }
        if (command.maximumTotalCad() != null && command.maximumTotalCad().signum() <= 0) {
            errors.add("maximumTotalCad must be positive");
        }
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
        return resolveOntarioMunicipality(command.city());
    }

    private Municipality resolveOntarioMunicipality(String city) {
        List<Municipality> matches = municipalities.findByNameOrAlias(city.trim());
        if (matches.isEmpty()) {
            throw new OutOfScopeLocationException(
                    "'" + city + "' is not a supported Ontario municipality", List.of());
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
