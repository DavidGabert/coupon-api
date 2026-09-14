package br.com.davidlopes.couponapi.application;

import br.com.davidlopes.couponapi.domain.Coupon;

import java.util.Optional;
import java.util.UUID;

/**
 * Port {@link CouponService} depends on to persist and retrieve coupons. The only
 * implementation today is a JPA adapter (see
 * {@code br.com.davidlopes.couponapi.infrastructure.JpaCouponRepository}), but this interface —
 * and therefore {@code CouponService} — has no knowledge of JPA, or of any persistence
 * technology at all.
 */
public interface CouponRepository {

    /**
     * Persists {@code coupon} and returns the persisted state. Implementations must make the
     * write, and any constraint it violates, visible to the caller synchronously — the caller
     * relies on catching a constraint violation from this call, not from some later point.
     *
     * @throws org.springframework.dao.DuplicateKeyException if {@code coupon}'s code is already
     *         in use by another active coupon
     * @throws org.springframework.dao.DataIntegrityViolationException for any other constraint
     *         the underlying store rejects the write for
     */
    Coupon save(Coupon coupon);

    Optional<Coupon> findById(UUID id);

    boolean existsActiveCouponWithCode(String code);
}
