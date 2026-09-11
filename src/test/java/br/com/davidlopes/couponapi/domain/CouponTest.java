package br.com.davidlopes.couponapi.domain;

import br.com.davidlopes.couponapi.domain.exception.CouponAlreadyDeletedException;
import br.com.davidlopes.couponapi.domain.exception.InvalidDiscountValueException;
import br.com.davidlopes.couponapi.domain.exception.PastExpirationDateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);

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
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);

        assertThatThrownBy(() ->
            Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), past, false))
            .isInstanceOf(PastExpirationDateException.class);
    }

    @Test
    void create_withBlankDescription_throws() {
        assertThatThrownBy(() ->
            Coupon.create("AB12CD", "   ", new BigDecimal("10.00"), FUTURE, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_onActiveCoupon_marksInactiveAndSetsDeletedAt() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);

        coupon.delete();

        assertThat(coupon.isActive()).isFalse();
        assertThat(coupon.getDeletedAt()).isNotNull();
    }

    @Test
    void delete_onAlreadyDeletedCoupon_throws() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
        coupon.delete();

        assertThatThrownBy(coupon::delete)
            .isInstanceOf(CouponAlreadyDeletedException.class);
    }
}
