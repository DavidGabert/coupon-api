package br.com.davidlopes.couponapi.infrastructure;

import br.com.davidlopes.couponapi.domain.Coupon;
import br.com.davidlopes.couponapi.infrastructure.support.AbstractCouponPersistenceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CouponRepositoryPostgresIT extends AbstractCouponPersistenceTest {

    private static final String RACE_CODE = "RACE01";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private CouponJpaRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    protected CouponJpaRepository repository() {
        return repository;
    }

    @Test
    void concurrentCreate_withSameCode_onlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        // Captures the id of whichever attempt actually commits, so it can be cleaned up
        // below: PROPAGATION_REQUIRES_NEW escapes the enclosing @Transactional rollback that
        // every other test in this class relies on, so the winning row would otherwise be
        // left behind in the shared container for the rest of the test class's lifetime.
        AtomicReference<UUID> committedId = new AtomicReference<>();

        try {
            List<Future<?>> futures = List.of(
                executor.submit(() -> attemptCreate(barrier, successCount, failureCount, committedId)),
                executor.submit(() -> attemptCreate(barrier, successCount, failureCount, committedId))
            );

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failureCount.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            deleteIfCommitted(committedId.get());
        }
    }

    private void attemptCreate(CyclicBarrier barrier, AtomicInteger successCount, AtomicInteger failureCount,
                                AtomicReference<UUID> committedId) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            template.executeWithoutResult(status -> {
                Coupon coupon = Coupon.create(RACE_CODE, "desc", new BigDecimal("10.00"),
                    Instant.now().plus(30, ChronoUnit.DAYS), false);
                repository.saveAndFlush(coupon);
                committedId.set(coupon.getId());
            });
            successCount.incrementAndGet();
        } catch (DataIntegrityViolationException e) {
            failureCount.incrementAndGet();
        }
    }

    private void deleteIfCommitted(UUID id) {
        if (id == null) {
            return;
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> repository.deleteById(id));
    }
}
