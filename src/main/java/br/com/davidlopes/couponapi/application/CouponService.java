package br.com.davidlopes.couponapi.application;

import br.com.davidlopes.couponapi.api.dto.CouponResponse;
import br.com.davidlopes.couponapi.api.dto.CreateCouponRequest;
import br.com.davidlopes.couponapi.domain.Coupon;
import br.com.davidlopes.couponapi.infrastructure.CouponJpaRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CouponService {

    private final CouponJpaRepository repository;

    public CouponService(CouponJpaRepository repository) {
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

        if (repository.existsByActiveCode(coupon.getCode().value())) {
            throw new DuplicateCouponCodeException(
                "Coupon code already in use: " + coupon.getCode().value());
        }

        try {
            Coupon saved = repository.save(coupon);
            return CouponResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // Every non-uniqueness cause (description length, discountValue precision) is
            // rejected by Coupon.create() before we get here, so the only constraint the
            // database can still be enforcing at this point is the unique active_code index,
            // lost to a concurrent create that committed between the check above and this save.
            throw new DuplicateCouponCodeException(
                "Coupon code already in use: " + coupon.getCode().value());
        }
    }

    @Cacheable(cacheNames = "coupons", key = "#id")
    @Transactional(readOnly = true)
    public CouponResponse findById(Long id) {
        Coupon coupon = repository.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + id));
        return CouponResponse.from(coupon);
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> findAll() {
        return repository.findAllByActiveTrue().stream()
            .map(CouponResponse::from)
            .toList();
    }

    @CacheEvict(cacheNames = "coupons", key = "#id")
    @Transactional
    public void delete(Long id) {
        Coupon coupon = repository.findById(id)
            .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + id));
        coupon.delete();
        repository.save(coupon);
    }
}
