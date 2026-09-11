package br.com.davidlopes.couponapi.domain;

import br.com.davidlopes.couponapi.domain.exception.InvalidCouponCodeException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public class CouponCode {

    private static final int REQUIRED_LENGTH = 6;

    @Column(name = "code", nullable = false, length = REQUIRED_LENGTH)
    private String value;

    protected CouponCode() {
        // required by JPA
    }

    private CouponCode(String value) {
        this.value = value;
    }

    public static CouponCode of(String rawCode) {
        if (rawCode == null) {
            throw new InvalidCouponCodeException("Coupon code must not be null");
        }
        String sanitized = rawCode.replaceAll("[^a-zA-Z0-9]", "");
        if (sanitized.length() != REQUIRED_LENGTH) {
            throw new InvalidCouponCodeException(
                "Coupon code must have exactly " + REQUIRED_LENGTH
                    + " alphanumeric characters after removing special characters, got "
                    + sanitized.length() + " from input: " + rawCode);
        }
        return new CouponCode(sanitized);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CouponCode other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
