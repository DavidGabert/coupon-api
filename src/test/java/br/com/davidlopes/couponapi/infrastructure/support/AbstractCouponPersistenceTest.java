package br.com.davidlopes.couponapi.infrastructure.support;

import br.com.davidlopes.couponapi.domain.Coupon;
import br.com.davidlopes.couponapi.infrastructure.CouponJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    private Coupon newCoupon(String code) {
        return Coupon.create(code, "desc", new BigDecimal("10.00"),
            LocalDateTime.now().plusDays(30), false);
    }

    @Test
    void save_thenFindByIdAndActiveTrue_returnsTheSavedCoupon() {
        Coupon saved = repository().save(newCoupon("AB12CD"));

        assertThat(repository().findByIdAndActiveTrue(saved.getId())).isPresent();
    }

    @Test
    void delete_thenFindByIdAndActiveTrue_returnsEmpty_butFindByIdStillFindsIt() {
        Coupon saved = repository().save(newCoupon("AB12CD"));
        saved.delete();
        repository().save(saved);

        assertThat(repository().findByIdAndActiveTrue(saved.getId())).isEmpty();
        assertThat(repository().findById(saved.getId())).isPresent();
    }

    @Test
    void delete_thenCodeCanBeReusedByANewCoupon() {
        Coupon first = repository().save(newCoupon("AB12CD"));
        first.delete();
        // Coupon uses IDENTITY generation, so a new entity's INSERT (persist) executes
        // immediately, but this update (merge) would otherwise stay queued until the next
        // flush. Without an explicit flush here, the second save() below (a fresh INSERT)
        // can hit the DB before this row's active_code is nulled out, tripping the unique
        // constraint even though the calls are made in the correct order.
        repository().saveAndFlush(first);

        Coupon second = repository().save(newCoupon("AB12CD"));

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(repository().existsByActiveCode("AB12CD")).isTrue();
    }

    @Test
    void findAllByActiveTrue_excludesDeletedCoupons() {
        Coupon active = repository().save(newCoupon("AB12CD"));
        Coupon toDelete = repository().save(newCoupon("EF34GH"));
        toDelete.delete();
        repository().save(toDelete);

        assertThat(repository().findAllByActiveTrue())
            .extracting(Coupon::getId)
            .containsExactly(active.getId());
    }

    @Test
    void save_withDuplicateActiveCode_violatesUniqueConstraint() {
        repository().saveAndFlush(newCoupon("AB12CD"));

        assertThatThrownBy(() -> repository().saveAndFlush(newCoupon("AB12CD")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
