package br.com.davidlopes.couponapi.infrastructure;

import br.com.davidlopes.couponapi.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponJpaRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByIdAndActiveTrue(UUID id);

    // Not called by any current service method (the list endpoint that used this was removed,
    // not part of the official contract) — kept as a repository-layer capability test
    // (AbstractCouponPersistenceTest#findAllByActiveTrue_excludesDeletedCoupons), same as
    // findByIdAndActiveTrue above.
    List<Coupon> findAllByActiveTrue();

    boolean existsByActiveCode(String activeCode);
}
