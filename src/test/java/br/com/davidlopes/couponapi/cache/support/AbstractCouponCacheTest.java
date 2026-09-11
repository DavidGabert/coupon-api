package br.com.davidlopes.couponapi.cache.support;

import br.com.davidlopes.couponapi.application.CouponService;
import br.com.davidlopes.couponapi.domain.Coupon;
import br.com.davidlopes.couponapi.infrastructure.CouponJpaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Shared cache-aside scenarios (hit avoids a repository call, evict on
 * delete clears the entry), reused by the mandatory in-memory-cache suite
 * ({@code CouponCacheTest}) and the optional Redis suite
 * ({@code CouponCacheRedisIT}).
 */
public abstract class AbstractCouponCacheTest {

    protected abstract CouponService service();

    protected abstract CouponJpaRepository repositorySpy();

    protected abstract org.springframework.cache.CacheManager cacheManager();

    @Test
    void findById_calledTwice_onlyHitsRepositoryOnce() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"),
            LocalDateTime.now().plusDays(30), false);
        var saved = repositorySpy().save(coupon);

        service().findById(saved.getId());
        service().findById(saved.getId());

        verify(repositorySpy(), times(1)).findByIdAndActiveTrue(saved.getId());
    }

    @Test
    void delete_evictsTheCacheEntry() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"),
            LocalDateTime.now().plusDays(30), false);
        var saved = repositorySpy().save(coupon);

        service().findById(saved.getId());
        assertThat(cacheManager().getCache("coupons").get(saved.getId())).isNotNull();

        service().delete(saved.getId());

        assertThat(cacheManager().getCache("coupons").get(saved.getId())).isNull();
    }
}
