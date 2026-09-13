package br.com.davidlopes.couponapi.domain;

import br.com.davidlopes.couponapi.domain.exception.CouponAlreadyDeletedException;
import br.com.davidlopes.couponapi.domain.exception.InvalidDescriptionException;
import br.com.davidlopes.couponapi.domain.exception.InvalidDiscountValueException;
import br.com.davidlopes.couponapi.domain.exception.PastExpirationDateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    private static final Instant FUTURE = Instant.now().plus(30, ChronoUnit.DAYS);

    @Test
    void create_whenPublishedOmittedAsFalse_defaultsToUnpublished() {
        Coupon coupon = Coupon.create("AB12CD", "10% off", new BigDecimal("10.00"), FUTURE, false);

        assertThat(coupon.isPublished()).isFalse();
        assertThat(coupon.isActive()).isTrue();
        assertThat(coupon.getDeletedAt()).isNull();
        assertThat(coupon.getCode().value()).isEqualTo("AB12CD");
    }

    @Test
    void create_withPublishedTrue_createsPublishedCoupon() {
        Coupon coupon = Coupon.create("AB12CD", "10% off", new BigDecimal("10.00"), FUTURE, true);

        assertThat(coupon.isPublished()).isTrue();
    }

    @Test
    void create_withDiscountValueExactlyMinimum_isValid() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("0.5"), FUTURE, false);

        assertThat(coupon.getDiscountValue()).isEqualByComparingTo("0.5");
    }

    @Test
    void create_withDiscountValueBelowMinimum_throws() {
        assertThatThrownBy(() ->
            Coupon.create("AB12CD", "desc", new BigDecimal("0.49"), FUTURE, false))
            .isInstanceOf(InvalidDiscountValueException.class);
    }

    @Test
    void create_withExpirationDateInThePast_throws() {
        Instant past = Instant.now().minus(1, ChronoUnit.MINUTES);

        assertThatThrownBy(() ->
            Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), past, false))
            .isInstanceOf(PastExpirationDateException.class);
    }

    @Test
    void create_withBlankDescription_throws() {
        assertThatThrownBy(() ->
            Coupon.create("AB12CD", "   ", new BigDecimal("10.00"), FUTURE, false))
            .isInstanceOf(InvalidDescriptionException.class);
    }

    @Test
    void create_withDescriptionAtMaximumLength_isValid() {
        String description = "x".repeat(255);

        Coupon coupon = Coupon.create("AB12CD", description, new BigDecimal("10.00"), FUTURE, false);

        assertThat(coupon.getDescription()).hasSize(255);
    }

    @Test
    void create_withDescriptionLongerThanMaximumLength_throws() {
        String description = "x".repeat(256);

        assertThatThrownBy(() ->
            Coupon.create("AB12CD", description, new BigDecimal("10.00"), FUTURE, false))
            .isInstanceOf(InvalidDescriptionException.class);
    }

    @Test
    void create_withDiscountValueExceedingColumnPrecision_throws() {
        // 20 integer digits: the discount_value column allows at most 17 (precision 19, scale 2)
        BigDecimal tooLarge = new BigDecimal("99999999999999999999.99");

        assertThatThrownBy(() ->
            Coupon.create("AB12CD", "desc", tooLarge, FUTURE, false))
            .isInstanceOf(InvalidDiscountValueException.class);
    }

    @Test
    void create_withDiscountValueAtMaximumColumnPrecision_isValid() {
        // 17 integer digits: the largest the discount_value column can hold
        BigDecimal largest = new BigDecimal("99999999999999999.99");

        Coupon coupon = Coupon.create("AB12CD", "desc", largest, FUTURE, false);

        assertThat(coupon.getDiscountValue()).isEqualByComparingTo(largest);
    }

    @Test
    void create_withDiscountValueThatRoundsAcrossThePrecisionBoundary_throws() {
        // 17 integer digits before rounding, but ".999" rounds HALF_UP to "1.00", carrying
        // the integer part to 100000000000000000 (18 digits) — one over what the column
        // allows. This only throws because Coupon.create() rounds BEFORE checking precision;
        // checking precision on the raw input first would let this through, then silently
        // exceed the discount_value column at persistence time.
        BigDecimal roundsOverTheLimit = new BigDecimal("99999999999999999.999");

        assertThatThrownBy(() ->
            Coupon.create("AB12CD", "desc", roundsOverTheLimit, FUTURE, false))
            .isInstanceOf(InvalidDiscountValueException.class);
    }

    @Test
    void create_withMoreThanTwoDecimalPlaces_roundsToWhatThePersistedColumnHolds() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("0.50000001"), FUTURE, false);

        assertThat(coupon.getDiscountValue()).isEqualByComparingTo("0.50");
        assertThat(coupon.getDiscountValue().scale()).isEqualTo(2);
    }

    @Test
    void create_returnsActiveStatusAndNeverRedeemed() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);

        assertThat(coupon.status()).isEqualTo(CouponStatus.ACTIVE);
        assertThat(coupon.isRedeemed()).isFalse();
    }

    @Test
    void status_asOfBeforeExpirationDate_isActive() {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), expiresAt, false);

        assertThat(coupon.status(expiresAt.minusSeconds(1))).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    void status_asOfAfterExpirationDate_isInactive() {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), expiresAt, false);

        assertThat(coupon.status(expiresAt.plusSeconds(1))).isEqualTo(CouponStatus.INACTIVE);
    }

    @Test
    void status_deletedCoupon_isDeletedRegardlessOfExpirationDate() {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), expiresAt, false);
        coupon.delete();

        // Even checked well before the expiration date, a deleted coupon is DELETED, not ACTIVE.
        assertThat(coupon.status(expiresAt.minusSeconds(1))).isEqualTo(CouponStatus.DELETED);
        assertThat(coupon.status(expiresAt.plusSeconds(1))).isEqualTo(CouponStatus.DELETED);
    }

    @Test
    void delete_onActiveCoupon_marksInactiveAndSetsDeletedAt() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);

        coupon.delete();

        assertThat(coupon.isActive()).isFalse();
        assertThat(coupon.getDeletedAt()).isNotNull();
        assertThat(coupon.status()).isEqualTo(CouponStatus.DELETED);
    }

    @Test
    void delete_onAlreadyDeletedCoupon_throws() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
        coupon.delete();

        assertThatThrownBy(coupon::delete)
            .isInstanceOf(CouponAlreadyDeletedException.class);
    }
}
