package com.reservationagent.hotelreservationagent.unit.application;

import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityGeocoder;
import com.reservationagent.hotelreservationagent.application.port.out.MunicipalityRepository;
import com.reservationagent.hotelreservationagent.application.service.pricing.OfferEvaluator;
import com.reservationagent.hotelreservationagent.application.service.validation.BookingRequestValidator;
import com.reservationagent.hotelreservationagent.application.service.validation.OutOfScopeLocationException;
import com.reservationagent.hotelreservationagent.domain.geography.GeoPoint;
import com.reservationagent.hotelreservationagent.domain.geography.Municipality;
import com.reservationagent.hotelreservationagent.domain.geography.Province;
import com.reservationagent.hotelreservationagent.domain.offer.HotelOffer;
import com.reservationagent.hotelreservationagent.domain.offer.PriceBreakdown;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import com.reservationagent.hotelreservationagent.domain.task.BookingTask;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.reservationagent.hotelreservationagent.support.Fixtures.CLOCK;
import static com.reservationagent.hotelreservationagent.support.Fixtures.NOW;
import static com.reservationagent.hotelreservationagent.support.Fixtures.breakdown;
import static com.reservationagent.hotelreservationagent.support.Fixtures.offer;
import static com.reservationagent.hotelreservationagent.support.Fixtures.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Searches cover all of Canada: city resolution, provincial sales tax, downtown tiebreak. */
class CanadaScopeTest {

    private final MunicipalityRepository municipalities = mock(MunicipalityRepository.class);
    private final MunicipalityGeocoder geocoder = mock(MunicipalityGeocoder.class);
    private final BookingRequestValidator validator = new BookingRequestValidator(municipalities, geocoder, CLOCK);

    private static Municipality city(String name, String province, String zone) {
        return new Municipality(UUID.randomUUID(), name, null, Set.of(), new GeoPoint(44.6, -63.6), ZoneId.of(zone),
                null, province);
    }

    @Test
    void aCityOutsideOntarioIsGeocodedWithItsProvince() {
        Municipality halifax = city("Halifax", "NS", "America/Halifax");
        when(municipalities.findByNameOrAlias(anyString())).thenReturn(List.of());
        when(geocoder.findCanadianLocality("Halifax", null)).thenReturn(Optional.of(
                new MunicipalityGeocoder.Locality("Halifax", new GeoPoint(44.65, -63.57), Province.NS)));
        when(municipalities.addGeocoded(eq("Halifax"), any(), eq(Province.NS))).thenReturn(halifax);

        Municipality resolved = validator.resolveMunicipality("Halifax", null);

        assertThat(resolved.provinceOrOntario()).isEqualTo(Province.NS);
        assertThat(resolved.timeZone()).isEqualTo(ZoneId.of("America/Halifax"));
    }

    @Test
    void aNamedProvinceSkipsTheOntarioTable() {
        Municipality windsorNs = city("Windsor", "NS", "America/Halifax");
        when(geocoder.findCanadianLocality("Windsor", Province.NS)).thenReturn(Optional.of(
                new MunicipalityGeocoder.Locality("Windsor", new GeoPoint(44.99, -64.13), Province.NS)));
        when(municipalities.addGeocoded(eq("Windsor"), any(), eq(Province.NS))).thenReturn(windsorNs);

        assertThat(validator.resolveMunicipality("Windsor", Province.NS)).isSameAs(windsorNs);
        verify(municipalities, never()).findByNameOrAlias(anyString());
    }

    @Test
    void anUnnamedProvincePrefersTheOntarioMatchThenAsks() {
        Municipality windsorOn = city("Windsor", "ON", "America/Toronto");
        Municipality windsorNs = city("Windsor", "NS", "America/Halifax");
        when(municipalities.findByNameOrAlias("Windsor")).thenReturn(List.of(windsorOn, windsorNs));
        assertThat(validator.resolveMunicipality("Windsor", null)).isSameAs(windsorOn);

        Municipality a = city("Richmond", "BC", "America/Vancouver");
        Municipality b = city("Richmond", "QC", "America/Montreal");
        when(municipalities.findByNameOrAlias("Richmond")).thenReturn(List.of(a, b));
        assertThatExceptionOfType(OutOfScopeLocationException.class)
                .isThrownBy(() -> validator.resolveMunicipality("Richmond", null))
                .satisfies(e -> assertThat(e.suggestions()).containsExactly("Richmond, BC", "Richmond, QC"));
    }

    @Test
    void notInCanadaIs422() {
        when(municipalities.findByNameOrAlias(anyString())).thenReturn(List.of());
        when(geocoder.findCanadianLocality(anyString(), any())).thenReturn(Optional.empty());
        assertThatExceptionOfType(OutOfScopeLocationException.class)
                .isThrownBy(() -> validator.resolveMunicipality("Seattle", null))
                .withMessageContaining("not a Canadian city");
    }

    @Test
    void theEstimateUsesTheProvincesSalesTax() {
        PriceBreakdown preTax = new PriceBreakdown(Money.cad("100.00"), null, null, null, null, null, TaxInclusion.EXCLUDED);
        assertThat(preTax.price(Province.AB.salesTaxRate()).orElseThrow().amount()).isEqualTo(Money.cad("105.00"));
        assertThat(preTax.price(Province.QC.salesTaxRate()).orElseThrow().amount()).isEqualTo(Money.cad("114.98"));
        assertThat(preTax.price(Province.NB.salesTaxRate()).orElseThrow().amount()).isEqualTo(Money.cad("115.00"));
        assertThat(Province.parse("Nova Scotia")).contains(Province.NS);
        assertThat(Province.parse("Québec")).contains(Province.QC);
        assertThat(Province.parse("bc")).contains(Province.BC);
    }

    @Test
    void equalPricesGoToTheHotelClosestToDowntown() {
        BookingTask task = task();
        HotelOffer far = offer(task, UUID.randomUUID(), breakdown("200.00", "26.00", "8.00", "0.00"), NOW);
        HotelOffer near = offer(task, UUID.randomUUID(), breakdown("200.00", "26.00", "8.00", "0.00"), NOW.plusSeconds(60));
        OfferEvaluator evaluator = new OfferEvaluator(CLOCK, new BigDecimal("0.06"), id -> PriceBreakdown.ONTARIO_HST_RATE,
                id -> Map.of(far.hotelId(), 4.2, near.hotelId(), 0.8));

        assertThat(evaluator.best(task, List.of(far, near))).contains(near);
        assertThat(evaluator.distanceKm(near)).contains(0.8);
    }
}
