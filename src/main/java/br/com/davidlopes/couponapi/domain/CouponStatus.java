package br.com.davidlopes.couponapi.domain;

/**
 * {@code ACTIVE}: not deleted, expiration date still in the future.
 * {@code INACTIVE}: not deleted, but the expiration date has passed.
 * {@code DELETED}: soft-deleted.
 *
 * <p>See {@link Coupon#status()}.
 */
public enum CouponStatus {
    ACTIVE,
    INACTIVE,
    DELETED
}
