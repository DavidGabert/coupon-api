package br.com.davidlopes.couponapi.infrastructure;

import br.com.davidlopes.couponapi.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponJpaRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByIdAndActiveTrue(UUID id);

    List<Coupon> findAllByActiveTrue();

    boolean existsByActiveCode(String activeCode);
}
