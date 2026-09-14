package br.com.davidlopes.couponapi.infrastructure.support;

import br.com.davidlopes.couponapi.domain.Coupon;
import br.com.davidlopes.couponapi.infrastructure.CouponEntity;
import br.com.davidlopes.couponapi.infrastructure.CouponJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Shared Create/Delete/uniqueness persistence scenarios, reused by the
 * mandatory H2 suite ({@code CouponRepositoryTest}) and the optional
 * Postgres suite ({@code CouponRepositoryPostgresIT}).
 *
 * <p>{@code @Transactional} is declared directly on this class (in addition to
 * the one implied by {@code @DataJpaTest} on the concrete subclass) because
 * Spring's transaction-attribute resolution for test methods
 * ({@code AbstractFallbackTransactionAttributeSource.computeTransactionAttribute})
 * only falls back to class-level {@code @Transactional} on
 * {@code specificMethod.getDeclaringClass()} — i.e. the class where the
 * {@code @Test} method is physically declared. For methods inherited here
 * (never overridden by the subclass), that is this abstract class, not the
 * {@code @DataJpaTest} subclass, so without this annotation the per-test
 * rollback silently never activates and writes leak across test methods.
 */
@Transactional
public abstract class AbstractCouponPersistenceTest {

    protected abstract CouponJpaRepository repository();

    private CouponEntity newCouponEntity(String code) {
        Coupon coupon = Coupon.create(code, "desc", new BigDecimal("10.00"),
            Instant.now().plus(30, ChronoUnit.DAYS), false);
        return CouponEntity.fromDomain(coupon);
    }

    /** Round-trips through the domain object, exactly like {@code CouponRepository.saveAndFlush} does. */
    private CouponEntity delete(CouponEntity entity) {
        Coupon domain = entity.toDomain();
        domain.delete();
        return CouponEntity.fromDomain(domain);
    }

    @Test
    void save_thenFindByIdAndActiveTrue_returnsTheSavedCoupon() {
        CouponEntity saved = repository().save(newCouponEntity("AB12CD"));

        assertThat(repository().findByIdAndActiveTrue(saved.getId())).isPresent();
    }

    @Test
    void delete_thenFindByIdAndActiveTrue_returnsEmpty_butFindByIdStillFindsIt() {
        CouponEntity saved = repository().save(newCouponEntity("AB12CD"));
        repository().save(delete(saved));

        assertThat(repository().findByIdAndActiveTrue(saved.getId())).isEmpty();
        assertThat(repository().findById(saved.getId())).isPresent();
    }

    @Test
    void delete_thenCodeCanBeReusedByANewCoupon() {
        CouponEntity first = repository().save(newCouponEntity("AB12CD"));
        // Without an explicit flush here, Hibernate is free to defer this write and execute
        // it in the same batch as (or after) the second save() below — the two statements'
        // relative order within a flush isn't otherwise guaranteed, and if the insert of the
        // second "AB12CD" row is issued before this row's active_code is nulled out, it trips
        // the unique constraint even though the calls were made in the correct order.
        repository().saveAndFlush(delete(first));

        CouponEntity second = repository().save(newCouponEntity("AB12CD"));

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(repository().existsByActiveCode("AB12CD")).isTrue();
    }

    @Test
    void findAllByActiveTrue_excludesDeletedCoupons() {
        CouponEntity active = repository().save(newCouponEntity("AB12CD"));
        CouponEntity toDelete = repository().save(newCouponEntity("EF34GH"));
        repository().save(delete(toDelete));

        assertThat(repository().findAllByActiveTrue())
            .extracting(CouponEntity::getId)
            .containsExactly(active.getId());
    }

    @Test
    void save_withDuplicateActiveCode_violatesUniqueConstraint() {
        repository().saveAndFlush(newCouponEntity("AB12CD"));

        assertThatThrownBy(() -> repository().saveAndFlush(newCouponEntity("AB12CD")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
