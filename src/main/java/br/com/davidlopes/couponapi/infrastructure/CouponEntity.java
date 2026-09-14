package br.com.davidlopes.couponapi.infrastructure;

import br.com.davidlopes.couponapi.domain.Coupon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for {@link Coupon}. Deliberately a separate class from the domain
 * object: {@code Coupon} carries every business rule and no persistence annotation; this class
 * carries the {@code coupons} table mapping and no business rule. {@link #fromDomain} and
 * {@link #toDomain} are the only two places that cross between them.
 */
@Entity
@Table(name = "coupons")
public class CouponEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Optimistic-locking version, managed by Hibernate — round-tripped through {@link Coupon#getVersion()}. */
    @Version
    private Long version;

    @Column(name = "code", nullable = false, length = 6)
    private String code;

    /**
     * Mirrors {@code code} while the coupon is active and is nulled out on delete. Backs the
     * "unique among active coupons only" rule via a plain {@code UNIQUE} constraint: two
     * deleted rows can share a {@code NULL} here without colliding, since SQL never considers
     * one {@code NULL} equal to another. Purely a persistence-layer trick — the domain has no
     * concept of it.
     */
    @Column(name = "active_code", unique = true)
    private String activeCode;

    @Column(nullable = false)
    private String description;

    @Column(name = "discount_value", nullable = false, precision = 38, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "expiration_date", nullable = false)
    private Instant expirationDate;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CouponEntity() {
        // required by JPA
    }

    public static CouponEntity fromDomain(Coupon coupon) {
        CouponEntity entity = new CouponEntity();
        entity.id = coupon.getId();
        entity.version = coupon.getVersion();
        entity.code = coupon.getCode().value();
        entity.activeCode = coupon.isActive() ? coupon.getCode().value() : null;
        entity.description = coupon.getDescription();
        entity.discountValue = coupon.getDiscountValue();
        entity.expirationDate = coupon.getExpirationDate();
        entity.published = coupon.isPublished();
        entity.active = coupon.isActive();
        entity.createdAt = coupon.getCreatedAt();
        entity.deletedAt = coupon.getDeletedAt();
        return entity;
    }

    public Coupon toDomain() {
        return Coupon.reconstitute(id, version, code, description, discountValue, expirationDate,
            published, active, createdAt, deletedAt);
    }

    public UUID getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }
}
