package br.com.davidlopes.couponapi.domain;

import br.com.davidlopes.couponapi.domain.exception.CouponAlreadyDeletedException;
import br.com.davidlopes.couponapi.domain.exception.InvalidDescriptionException;
import br.com.davidlopes.couponapi.domain.exception.InvalidDiscountValueException;
import br.com.davidlopes.couponapi.domain.exception.PastExpirationDateException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
public class Coupon {

    private static final BigDecimal MINIMUM_DISCOUNT_VALUE = new BigDecimal("0.5");

    /** Mirrors the {@code description} column, which is the JPA default {@code varchar(255)}. */
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    /** Mirrors the {@code discount_value} column: precision 19, scale 2 leaves 17 integer digits. */
    private static final int MAX_DISCOUNT_INTEGER_DIGITS = 17;

    /** Mirrors the scale of the {@code discount_value} column. */
    private static final int DISCOUNT_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private CouponCode code;

    @Column(name = "active_code", unique = true)
    private String activeCode;

    @Column(nullable = false)
    private String description;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "expiration_date", nullable = false)
    private LocalDateTime expirationDate;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Coupon() {
        // required by JPA
    }

    private Coupon(CouponCode code, String description, BigDecimal discountValue,
                    LocalDateTime expirationDate, boolean published) {
        this.code = code;
        this.activeCode = code.value();
        this.description = description;
        this.discountValue = discountValue;
        this.expirationDate = expirationDate;
        this.published = published;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public static Coupon create(String rawCode, String description, BigDecimal discountValue,
                                 LocalDateTime expirationDate, boolean published) {
        CouponCode couponCode = CouponCode.of(rawCode);

        if (discountValue == null || discountValue.compareTo(MINIMUM_DISCOUNT_VALUE) < 0) {
            throw new InvalidDiscountValueException(
                "discountValue must be >= " + MINIMUM_DISCOUNT_VALUE + ", got: " + discountValue);
        }

        // Rounded up front so the in-memory entity — and therefore the create response —
        // already carries exactly the value the discount_value column will hold. Without
        // this, a value such as 0.50000001 was echoed back verbatim but persisted as 0.50,
        // leaving the create response disagreeing with every later read.
        BigDecimal normalizedDiscountValue = discountValue.setScale(DISCOUNT_SCALE, RoundingMode.HALF_UP);

        if (integerDigits(normalizedDiscountValue) > MAX_DISCOUNT_INTEGER_DIGITS) {
            throw new InvalidDiscountValueException(
                "discountValue must have at most " + MAX_DISCOUNT_INTEGER_DIGITS
                    + " integer digits, got: " + discountValue);
        }

        if (expirationDate == null || expirationDate.isBefore(LocalDateTime.now())) {
            throw new PastExpirationDateException(
                "expirationDate must not be in the past: " + expirationDate);
        }

        if (description == null || description.isBlank()) {
            throw new InvalidDescriptionException("description must not be blank");
        }

        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidDescriptionException(
                "description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters, got: "
                    + description.length());
        }

        return new Coupon(couponCode, description, normalizedDiscountValue, expirationDate, published);
    }

    private static int integerDigits(BigDecimal value) {
        return value.precision() - value.scale();
    }

    public void delete() {
        if (!this.active) {
            throw new CouponAlreadyDeletedException("Coupon " + id + " is already deleted");
        }
        this.active = false;
        this.activeCode = null;
        this.deletedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public CouponCode getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public LocalDateTime getExpirationDate() {
        return expirationDate;
    }

    public boolean isPublished() {
        return published;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
