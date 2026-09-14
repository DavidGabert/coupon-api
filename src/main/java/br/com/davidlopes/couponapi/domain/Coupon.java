package br.com.davidlopes.couponapi.domain;

import br.com.davidlopes.couponapi.domain.exception.CouponAlreadyDeletedException;
import br.com.davidlopes.couponapi.domain.exception.InvalidDescriptionException;
import br.com.davidlopes.couponapi.domain.exception.InvalidDiscountValueException;
import br.com.davidlopes.couponapi.domain.exception.PastExpirationDateException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Rich domain object: owns every Create/Delete business rule. Deliberately free of any
 * persistence annotation — how a {@code Coupon} is stored is the concern of the infrastructure
 * layer's own persistence model, not of this class. See
 * {@code br.com.davidlopes.couponapi.infrastructure.CouponEntity}, the only place that
 * translates between the two.
 */
public class Coupon {

    private static final BigDecimal MINIMUM_DISCOUNT_VALUE = new BigDecimal("0.5");

    private static final int MAX_DESCRIPTION_LENGTH = 255;

    /** Scale a discount value is rounded to before being stored or returned. */
    private static final int DISCOUNT_SCALE = 2;

    private UUID id;

    /**
     * Optimistic-locking version. Makes {@link #delete()} safe under concurrency: the
     * in-memory {@code active} check alone cannot stop two simultaneous deletes from both
     * committing, but the version check the persistence layer performs on the UPDATE can.
     */
    private Long version;

    private CouponCode code;
    private String description;
    private BigDecimal discountValue;
    private Instant expirationDate;
    private boolean published;
    private boolean active;
    private Instant createdAt;
    private Instant deletedAt;

    private Coupon(CouponCode code, String description, BigDecimal discountValue,
                    Instant expirationDate, boolean published) {
        this.code = code;
        this.description = description;
        this.discountValue = discountValue;
        this.expirationDate = expirationDate;
        this.published = published;
        this.active = true;
        this.createdAt = Instant.now();
    }

    private Coupon(UUID id, Long version, CouponCode code, String description, BigDecimal discountValue,
                    Instant expirationDate, boolean published, boolean active, Instant createdAt,
                    Instant deletedAt) {
        this.id = id;
        this.version = version;
        this.code = code;
        this.description = description;
        this.discountValue = discountValue;
        this.expirationDate = expirationDate;
        this.published = published;
        this.active = active;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    public static Coupon create(String rawCode, String description, BigDecimal discountValue,
                                 Instant expirationDate, boolean published) {
        CouponCode couponCode = CouponCode.of(rawCode);

        if (discountValue == null) {
            throw new InvalidDiscountValueException(
                "discountValue must be >= " + MINIMUM_DISCOUNT_VALUE + ", got: null");
        }

        // Rounded up front so the in-memory coupon — and therefore the create response —
        // already carries exactly the value that gets persisted. Without this, a value such
        // as 0.50000001 was echoed back verbatim but persisted as 0.50, leaving the create
        // response disagreeing with every later read. There is no upper bound on discountValue:
        // rounding only fixes the scale, it never rejects a value for being large.
        BigDecimal normalizedDiscountValue = discountValue.setScale(DISCOUNT_SCALE, RoundingMode.HALF_UP);

        // Checked against the rounded value, not the raw input: a raw 0.495 rounds HALF_UP to
        // the minimum itself (0.50) and must be accepted, since that rounded value is what
        // actually gets stored and returned -- rejecting it here would contradict what every
        // later read of the same coupon shows.
        if (normalizedDiscountValue.compareTo(MINIMUM_DISCOUNT_VALUE) < 0) {
            throw new InvalidDiscountValueException(
                "discountValue must be >= " + MINIMUM_DISCOUNT_VALUE + ", got: " + discountValue);
        }

        if (expirationDate == null || expirationDate.isBefore(Instant.now())) {
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

    /**
     * Rebuilds a {@code Coupon} from already-valid, already-persisted state — used only by
     * the infrastructure layer when mapping a stored record back into the domain. Skips every
     * business rule in {@link #create}, since a persisted coupon was valid at the moment it was
     * written; this is reconstruction, not creation. It still guards the one structural
     * invariant no valid persisted row can violate — {@code active} and {@code deletedAt} must
     * agree — since a violation here means the persisted state itself is corrupt, not that a
     * business rule was broken.
     */
    public static Coupon reconstitute(UUID id, Long version, String code, String description,
                                       BigDecimal discountValue, Instant expirationDate, boolean published,
                                       boolean active, Instant createdAt, Instant deletedAt) {
        if (active == (deletedAt != null)) {
            throw new IllegalStateException(
                "Inconsistent persisted coupon state for " + id + ": active=" + active
                    + " but deletedAt=" + deletedAt);
        }
        return new Coupon(id, version, CouponCode.of(code), description, discountValue, expirationDate,
            published, active, createdAt, deletedAt);
    }

    public void delete() {
        if (!this.active) {
            throw new CouponAlreadyDeletedException("Coupon " + id + " is already deleted");
        }
        this.active = false;
        this.deletedAt = Instant.now();
    }

    public CouponStatus status() {
        return status(Instant.now());
    }

    /**
     * Package-private so {@code CouponTest} can assert the ACTIVE/INACTIVE boundary at an
     * exact instant without sleeping past a real expiration date — {@link #status()} is the
     * only entry point production code ever calls.
     */
    CouponStatus status(Instant asOf) {
        if (!active) {
            return CouponStatus.DELETED;
        }
        return expirationDate.isBefore(asOf) ? CouponStatus.INACTIVE : CouponStatus.ACTIVE;
    }

    /** Always {@code false}: no redemption/usage-tracking rule has been specified yet. */
    public boolean isRedeemed() {
        return false;
    }

    public UUID getId() {
        return id;
    }

    public Long getVersion() {
        return version;
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

    public Instant getExpirationDate() {
        return expirationDate;
    }

    public boolean isPublished() {
        return published;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
