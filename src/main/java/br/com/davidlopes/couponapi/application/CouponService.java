package br.com.davidlopes.couponapi.application;

import br.com.davidlopes.couponapi.domain.Coupon;
import br.com.davidlopes.couponapi.domain.exception.CouponAlreadyDeletedException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CouponService {

    private final CouponRepository repository;

    public CouponService(CouponRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CouponResponse create(CreateCouponRequest request) {
        Coupon coupon = Coupon.create(
            request.code(),
            request.description(),
            request.discountValue(),
            request.expirationDate(),
            Boolean.TRUE.equals(request.published())
        );

        if (repository.existsActiveCouponWithCode(coupon.getCode().value())) {
            throw new DuplicateCouponCodeException(
                "Coupon code already in use: " + coupon.getCode().value());
        }

        try {
            // The port's save() contract guarantees the write, and any constraint it violates,
            // is visible synchronously here — see CouponRepository.save's javadoc for why that
            // matters for this exact catch.
            Coupon saved = repository.save(coupon);
            return CouponResponse.from(saved);
        } catch (DuplicateKeyException e) {
            // Only DuplicateKeyException is caught here, not the broader
            // DataIntegrityViolationException it extends: the adapter only throws this specific
            // subtype when the violation really was the active_code constraint (see
            // JpaCouponRepository.save). Anything else -- e.g. a discountValue too large for the
            // column -- is a genuinely different problem and is left to propagate rather than
            // being misreported as a duplicate code.
            throw new DuplicateCouponCodeException(
                "Coupon code already in use: " + coupon.getCode().value());
        }
    }

    // Unfiltered lookup, deliberately: the API contract returns soft-deleted coupons too,
    // with status DELETED, rather than 404ing them — findByIdAndActiveTrue would hide them.
    @Cacheable(cacheNames = "coupons", key = "#id")
    @Transactional(readOnly = true)
    public CouponResponse findById(UUID id) {
        Coupon coupon = repository.findById(id)
            .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + id));
        return CouponResponse.from(coupon);
    }

    @CacheEvict(cacheNames = "coupons", key = "#id")
    @Transactional
    public void delete(UUID id) {
        Coupon coupon = repository.findById(id)
            .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + id));
        coupon.delete();
        try {
            repository.save(coupon);
        } catch (ObjectOptimisticLockingFailureException e) {
            // A concurrent delete of the same coupon committed first and bumped the version,
            // so this UPDATE matched no row. coupon.delete()'s in-memory check could not see
            // that; from this caller's point of view the coupon was indeed already deleted.
            throw new CouponAlreadyDeletedException("Coupon " + id + " is already deleted");
        }
    }
}
