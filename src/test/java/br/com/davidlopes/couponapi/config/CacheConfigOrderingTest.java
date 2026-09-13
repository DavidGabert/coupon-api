package br.com.davidlopes.couponapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the fix for the known cache/transaction ordering race directly, by inspecting the
 * actual Spring AOP advisor precedence, rather than trying to observe it through a real
 * concurrent timing window — the same kind of race this session already found to be flaky
 * and unreliable to assert on when testing the optimistic-lock fix's Redis-backed sibling
 * suite. A lower {@code order} value means higher precedence, i.e. that advisor wraps
 * OUTSIDE the other — so the cache advisor must have the lower value for
 * {@code @CacheEvict} on {@code CouponService.delete()} to fire only after the surrounding
 * {@code @Transactional} call, commit included, has already returned.
 *
 * <p>Without {@code CacheConfig}'s explicit {@code @EnableCaching(order = 0)}, both advisors
 * default to {@code Ordered.LOWEST_PRECEDENCE} and this assertion fails (order is equal, not
 * strictly less) — that failure is exactly the previously-documented "not guaranteed"
 * ordering this test now pins down.
 */
@SpringBootTest
class CacheConfigOrderingTest {

    @Autowired
    private BeanFactoryCacheOperationSourceAdvisor cacheAdvisor;

    @Autowired
    private BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor;

    @Test
    void cacheAdvisorWrapsOutsideTransactionAdvisor_soEvictionWaitsForCommit() {
        assertThat(cacheAdvisor.getOrder()).isLessThan(transactionAdvisor.getOrder());
    }
}
