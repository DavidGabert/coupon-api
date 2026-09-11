package br.com.davidlopes.couponapi.application;

import br.com.davidlopes.couponapi.api.dto.CouponResponse;
import br.com.davidlopes.couponapi.api.dto.CreateCouponRequest;
import br.com.davidlopes.couponapi.domain.Coupon;
import br.com.davidlopes.couponapi.infrastructure.CouponJpaRepository;
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
            throw new DuplicateCouponCodeException(
                "Coupon code already in use: " + coupon.getCode().value());
        }
    }

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

    @Transactional
    public void delete(Long id) {
        Coupon coupon = repository.findById(id)
            .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + id));
        coupon.delete();
        repository.save(coupon);
    }
}
