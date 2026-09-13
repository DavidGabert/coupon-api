package br.com.davidlopes.couponapi.application;

import br.com.davidlopes.couponapi.api.dto.CouponResponse;
import br.com.davidlopes.couponapi.api.dto.CreateCouponRequest;
import br.com.davidlopes.couponapi.domain.exception.CouponAlreadyDeletedException;
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
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deliberately NOT {@code @Transactional}: the race this proves only exists between two
 * independent transactions that really commit, so the test must create the coupon in a
 * committed transaction and let each racing thread run its own, then clean up by hand.
 */
@SpringBootTest
class CouponServiceConcurrentDeleteTest {

    private static final String RACE_CODE = "DELRC1";

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponJpaRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentDelete_ofTheSameCoupon_onlyOneSucceeds() throws Exception {
        UUID id = createCommittedCoupon();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyDeletedCount = new AtomicInteger(0);

        try {
            List<Future<?>> futures = List.of(
                executor.submit(() -> attemptDelete(id, barrier, successCount, alreadyDeletedCount)),
                executor.submit(() -> attemptDelete(id, barrier, successCount, alreadyDeletedCount))
            );

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(alreadyDeletedCount.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            deleteRow(id);
        }
    }

    private UUID createCommittedCoupon() {
        return newTransaction().execute(status -> {
            CouponResponse created = couponService.create(new CreateCouponRequest(
                RACE_CODE, "desc", new BigDecimal("10.00"), Instant.now().plus(30, ChronoUnit.DAYS), false));
            return created.id();
        });
    }

    private void attemptDelete(UUID id, CyclicBarrier barrier,
                                AtomicInteger successCount, AtomicInteger alreadyDeletedCount) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            newTransaction().executeWithoutResult(status -> couponService.delete(id));
            successCount.incrementAndGet();
        } catch (CouponAlreadyDeletedException e) {
            alreadyDeletedCount.incrementAndGet();
        }
    }

    private void deleteRow(UUID id) {
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
