package br.com.davidlopes.couponapi.infrastructure;

import br.com.davidlopes.couponapi.application.CouponRepository;
import br.com.davidlopes.couponapi.domain.Coupon;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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
        try {
            // saveAndFlush, not save: CouponEntity's id is client-generated
            // (GenerationType.UUID), so Hibernate has no reason to flush a new row before
            // commit. The port's contract promises the write (and any constraint violation) is
            // visible synchronously, so this adapter must always flush to honor it.
            return jpaRepository.saveAndFlush(CouponEntity.fromDomain(coupon)).toDomain();
        } catch (DataIntegrityViolationException e) {
            // active_code is the only unique constraint on this table, so a constraint
            // violation here is unambiguously that one -- no second query needed to check
            // which constraint fired. That matters beyond convenience: once a flush fails,
            // Hibernate's persistence context for this transaction is left with the failed
            // insert still pending, and any further operation that triggers an auto-flush
            // (any query included) re-attempts it and throws the same exception again. The
            // caller must be able to tell "definitely a duplicate code" from "some other data
            // problem" from this exception alone, without touching the repository again.
            // Re-typed as DuplicateKeyException -- a Spring DAO type, not a Hibernate one --
            // so the application layer can react to it without depending on Hibernate.
            if (isUniqueConstraintViolation(e)) {
                throw new DuplicateKeyException("Coupon code already in use: " + coupon.getCode().value(), e);
            }
            throw e;
        }
    }

    /**
     * Walks the full cause chain, not just the immediate cause: Spring wraps Hibernate's
     * {@link ConstraintViolationException} directly under {@link DataIntegrityViolationException}
     * today, but relying on that exact nesting depth would be fragile to a JPA provider or
     * dialect change. {@code getMostSpecificCause()} isn't the right tool here either -- it
     * would walk past this Hibernate-level type down into the raw JDBC driver exception.
     */
    private static boolean isUniqueConstraintViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException) {
                return true;
            }
        }
        return false;
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
