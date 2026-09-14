package br.com.davidlopes.couponapi.infrastructure;

import br.com.davidlopes.couponapi.application.CouponRepository;
import br.com.davidlopes.couponapi.domain.Coupon;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter for the {@link CouponRepository} port. Wraps {@link CouponJpaRepository} and does
 * the {@link Coupon} ↔ {@link CouponEntity} translation — the only class that depends on both.
 */
@Repository
public class JpaCouponRepository implements CouponRepository {

    private final CouponJpaRepository jpaRepository;

    public JpaCouponRepository(CouponJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Coupon save(Coupon coupon) {
        // saveAndFlush, not save: CouponEntity's id is client-generated (GenerationType.UUID),
        // so Hibernate has no reason to flush a new row before commit. The port's contract
        // promises the write (and any constraint violation) is visible synchronously, so this
        // adapter must always flush to honor it.
        return jpaRepository.saveAndFlush(CouponEntity.fromDomain(coupon)).toDomain();
    }

    @Override
    public Optional<Coupon> findById(UUID id) {
        return jpaRepository.findById(id).map(CouponEntity::toDomain);
    }

    @Override
    public boolean existsActiveCouponWithCode(String code) {
        return jpaRepository.existsByActiveCode(code);
    }
}
