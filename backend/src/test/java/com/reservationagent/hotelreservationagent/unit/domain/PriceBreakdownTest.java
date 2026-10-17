package com.reservationagent.hotelreservationagent.unit.domain;

import com.reservationagent.hotelreservationagent.domain.offer.PriceBreakdown;
import com.reservationagent.hotelreservationagent.domain.offer.TaxInclusion;
import com.reservationagent.hotelreservationagent.domain.shared.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The comparable all-in total: computed from components when all are known, else a stated tax-inclusive total. */
class PriceBreakdownTest {

    private static Money cad(String s) {
        return s == null ? null : Money.cad(s);
    }

    private static PriceBreakdown breakdown(String room, String hst, String mat, String fees, String optional,
                                            String allIn, TaxInclusion inclusion) {
        return new PriceBreakdown(cad(room), cad(hst), cad(mat), cad(fees), cad(optional), cad(allIn), inclusion);
    }

    @Test
    void sumsEveryMandatoryComponentButNotOptionalFees() {
        PriceBreakdown p = breakdown("199.00", "25.87", "7.96", "15.00", "30.00", null, TaxInclusion.EXCLUDED);
        assertThat(p.computedTotal()).contains(cad("247.83"));
        assertThat(p.comparableTotal()).contains(cad("247.83"));
    }

    @Test
    void zeroComponentsCountAsStatedNone() {
        PriceBreakdown p = breakdown("200", "26", "0", "0", null, null, TaxInclusion.UNKNOWN);
        assertThat(p.comparableTotal()).contains(cad("226.00"));
    }

    @ParameterizedTest(name = "missing {0}")
    @CsvSource(nullValues = "null", value = {
            "room,null,26,8,0", "hst,200,null,8,0", "mat,200,26,null,0", "fees,200,26,8,null"})
    void anyUnknownComponentMeansNoComputedTotal(String missing, String room, String hst, String mat, String fees) {
        PriceBreakdown p = breakdown(room, hst, mat, fees, null, null, TaxInclusion.EXCLUDED);
        assertThat(p.computedTotal()).isEmpty();
        assertThat(p.comparableTotal()).isEmpty();
    }

    @Test
    void fallsBackToAStatedTotalThatIncludesTaxes() {
        PriceBreakdown p = breakdown("200", null, null, null, null, "248.50", TaxInclusion.INCLUDED);
        assertThat(p.computedTotal()).isEmpty();
        assertThat(p.comparableTotal()).contains(cad("248.50"));
    }

    @ParameterizedTest
    @EnumSource(value = TaxInclusion.class, names = {"EXCLUDED", "UNKNOWN"})
    void neverTrustsAStatedTotalThatMayExcludeTaxes(TaxInclusion inclusion) {
        PriceBreakdown p = breakdown(null, null, null, null, null, "199.00", inclusion);
        assertThat(p.comparableTotal()).isEmpty();
    }

    @Test
    void computedSumWinsOverTheStatedTotal() {
        PriceBreakdown p = breakdown("200", "26", "8", "0", null, "250.00", TaxInclusion.INCLUDED);
        assertThat(p.comparableTotal()).contains(cad("234.00"));
    }

    @Test
    void nullTaxInclusionIsUnknownNeverIncluded() {
        PriceBreakdown p = breakdown(null, null, null, null, null, "199.00", null);
        assertThat(p.taxInclusion()).isEqualTo(TaxInclusion.UNKNOWN);
        assertThat(p.comparableTotal()).isEmpty();
    }

    @Test
    void nothingKnownMeansNothingComparable() {
        assertThat(breakdown(null, null, null, null, null, null, null).comparableTotal()).isEmpty();
    }

    @ParameterizedTest(name = "stated {0} vs computed 234.00 -> disagree={1}")
    @CsvSource({"234.00,false", "235.00,false", "233.00,false", "235.01,true", "232.99,true", "300.00,true"})
    void flagsDisagreementBeyondOneDollar(String stated, boolean disagree) {
        PriceBreakdown p = breakdown("200", "26", "8", "0", null, stated, TaxInclusion.INCLUDED);
        assertThat(p.totalsDisagree()).isEqualTo(disagree);
        assertThat(PriceBreakdown.DISAGREEMENT_TOLERANCE).isEqualTo(cad("1.00"));
    }

    @Test
    void noDisagreementWithoutBothTotals() {
        assertThat(breakdown("200", "26", "8", "0", null, null, TaxInclusion.EXCLUDED).totalsDisagree()).isFalse();
        assertThat(breakdown("200", null, null, null, null, "500", TaxInclusion.INCLUDED).totalsDisagree()).isFalse();
    }
}
