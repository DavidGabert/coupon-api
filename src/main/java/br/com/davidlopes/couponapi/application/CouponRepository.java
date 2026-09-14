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

    Coupon save(Coupon coupon);

    Coupon saveAndFlush(Coupon coupon);

    Optional<Coupon> findById(UUID id);

    boolean existsByActiveCode(String activeCode);
}
