package br.com.davidlopes.couponapi.infrastructure;

import br.com.davidlopes.couponapi.infrastructure.support.AbstractCouponPersistenceTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class CouponRepositoryTest extends AbstractCouponPersistenceTest {

    @Autowired
    private CouponJpaRepository repository;

    @Override
    protected CouponJpaRepository repository() {
        return repository;
    }
}
