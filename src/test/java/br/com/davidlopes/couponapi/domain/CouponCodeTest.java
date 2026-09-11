package br.com.davidlopes.couponapi.domain;

import br.com.davidlopes.couponapi.domain.exception.InvalidCouponCodeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponCodeTest {

    @Test
    void of_withExactlySixAlphanumericChars_keepsValueAsIs() {
        CouponCode code = CouponCode.of("AB12CD");

        assertThat(code.value()).isEqualTo("AB12CD");
    }

    @Test
    void of_withSpecialCharsThatLeaveExactlySixAlphanumeric_stripsSpecialChars() {
        CouponCode code = CouponCode.of("AB-12#CD");

        assertThat(code.value()).isEqualTo("AB12CD");
    }

    @Test
    void of_whenStrippedResultHasFewerThanSixChars_throws() {
        assertThatThrownBy(() -> CouponCode.of("AB-1#2"))
            .isInstanceOf(InvalidCouponCodeException.class);
    }

    @Test
    void of_whenStrippedResultHasMoreThanSixChars_throws() {
        assertThatThrownBy(() -> CouponCode.of("ABCDEFGH"))
            .isInstanceOf(InvalidCouponCodeException.class);
    }

    @Test
    void of_withNull_throws() {
        assertThatThrownBy(() -> CouponCode.of(null))
            .isInstanceOf(InvalidCouponCodeException.class);
    }

    @Test
    void of_withOnlySpecialChars_throws() {
        assertThatThrownBy(() -> CouponCode.of("!!!!!!"))
            .isInstanceOf(InvalidCouponCodeException.class);
    }

    @Test
    void twoCouponCodes_withSameValue_areEqual() {
        assertThat(CouponCode.of("AB12CD")).isEqualTo(CouponCode.of("AB12CD"));
    }
}
