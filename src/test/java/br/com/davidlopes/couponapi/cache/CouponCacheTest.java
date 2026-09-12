package br.com.davidlopes.couponapi.cache;

import br.com.davidlopes.couponapi.application.CouponService;
import br.com.davidlopes.couponapi.cache.support.AbstractCouponCacheTest;
import br.com.davidlopes.couponapi.infrastructure.CouponJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.cache.CacheManager;

@SpringBootTest
class CouponCacheTest extends AbstractCouponCacheTest {

    @Autowired
    private CouponService service;

    @MockitoSpyBean
    private CouponJpaRepository repository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Override
    protected CouponService service() {
        return service;
    }

    @Override
    protected CouponJpaRepository repositorySpy() {
        return repository;
    }

    @Override
    protected CacheManager cacheManager() {
        return cacheManager;
    }
}
