package br.com.davidlopes.couponapi.application;

import br.com.davidlopes.couponapi.infrastructure.CouponJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@code CouponService.create()}'s duplicate-code handling against real Hibernate flush
 * timing, not a mock: {@code CouponEntity}'s id is client-generated ({@code GenerationType.UUID}),
 * so a plain {@code save()} has no reason to flush before commit, and the unique {@code
 * active_code} constraint violation from a losing concurrent create would surface at commit time,
 * outside create()'s own try/catch — exactly the bug {@code saveAndFlush} fixes. A test that mocks
 * the repository to throw synchronously cannot tell the two apart; this one, with two real threads
 * racing through real transactions against H2, can.
 *
 * <p>Deliberately NOT {@code @Transactional}, for the same reason as {@code
 * CouponServiceConcurrentDeleteTest}: the race only exists between two independent transactions
 * that really commit.
 */
@SpringBootTest
class CouponServiceConcurrentCreateTest {

    private static final String RACE_CODE = "CRERC1";

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponJpaRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentCreate_withSameCode_onlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicReference<java.util.UUID> committedId = new AtomicReference<>();

        try {
            List<Future<?>> futures = List.of(
                executor.submit(() -> attemptCreate(barrier, successCount, duplicateCount, committedId)),
                executor.submit(() -> attemptCreate(barrier, successCount, duplicateCount, committedId))
            );

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(duplicateCount.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            deleteIfCommitted(committedId.get());
        }
    }

    private void attemptCreate(CyclicBarrier barrier, AtomicInteger successCount,
                                AtomicInteger duplicateCount, AtomicReference<java.util.UUID> committedId) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            newTransaction().executeWithoutResult(status -> {
                var created = couponService.create(new CreateCouponRequest(
                    RACE_CODE, "desc", new BigDecimal("10.00"), Instant.now().plus(30, ChronoUnit.DAYS), false));
                committedId.set(created.id());
            });
            successCount.incrementAndGet();
        } catch (DuplicateCouponCodeException e) {
            duplicateCount.incrementAndGet();
        }
    }

    private void deleteIfCommitted(java.util.UUID id) {
        if (id == null) {
            return;
        }
        newTransaction().executeWithoutResult(status -> repository.deleteById(id));
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
