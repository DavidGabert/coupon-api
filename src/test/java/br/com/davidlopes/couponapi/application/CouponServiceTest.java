package br.com.davidlopes.couponapi.application;

import br.com.davidlopes.couponapi.api.dto.CouponResponse;
import br.com.davidlopes.couponapi.api.dto.CreateCouponRequest;
import br.com.davidlopes.couponapi.domain.Coupon;
import br.com.davidlopes.couponapi.domain.CouponStatus;
import br.com.davidlopes.couponapi.domain.exception.CouponAlreadyDeletedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository repository;

    @InjectMocks
    private CouponService service;

    private static final Instant FUTURE = Instant.now().plus(30, ChronoUnit.DAYS);
    private static final UUID ID = UUID.randomUUID();

    private CreateCouponRequest validRequest() {
        return new CreateCouponRequest("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
    }

    @Test
    void create_withNoExistingCode_savesAndReturnsResponse() {
        when(repository.existsByActiveCode("AB12CD")).thenReturn(false);
        when(repository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponResponse response = service.create(validRequest());

        assertThat(response.code()).isEqualTo("AB12CD");
        assertThat(response.status()).isEqualTo(CouponStatus.ACTIVE);
        assertThat(response.redeemed()).isFalse();
    }

    @Test
    void create_withExistingActiveCode_throwsDuplicateWithoutCallingSave() {
        when(repository.existsByActiveCode("AB12CD")).thenReturn(true);

        assertThatThrownBy(() -> service.create(validRequest()))
            .isInstanceOf(DuplicateCouponCodeException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void create_whenSaveRacesIntoAConstraintViolation_translatesToDuplicateCouponCodeException() {
        when(repository.existsByActiveCode("AB12CD")).thenReturn(false);
        when(repository.save(any(Coupon.class))).thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> service.create(validRequest()))
            .isInstanceOf(DuplicateCouponCodeException.class);
    }

    @Test
    void findById_whenActiveCouponExists_returnsResponse() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
        when(repository.findById(ID)).thenReturn(Optional.of(coupon));

        CouponResponse response = service.findById(ID);

        assertThat(response.code()).isEqualTo("AB12CD");
        assertThat(response.status()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    void findById_whenCouponIsSoftDeleted_returnsResponseWithDeletedStatus_notNotFound() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
        coupon.delete();
        when(repository.findById(ID)).thenReturn(Optional.of(coupon));

        CouponResponse response = service.findById(ID);

        assertThat(response.status()).isEqualTo(CouponStatus.DELETED);
    }

    @Test
    void findById_whenNotFound_throwsCouponNotFoundException() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(ID))
            .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void delete_whenCouponExists_marksItDeletedAndSaves() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
        when(repository.findById(ID)).thenReturn(Optional.of(coupon));

        service.delete(ID);

        assertThat(coupon.isActive()).isFalse();
        verify(repository).saveAndFlush(coupon);
    }

    @Test
    void delete_whenFlushLosesTheOptimisticLockRace_translatesToCouponAlreadyDeletedException() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
        when(repository.findById(ID)).thenReturn(Optional.of(coupon));
        when(repository.saveAndFlush(coupon))
            .thenThrow(new ObjectOptimisticLockingFailureException(Coupon.class, ID));

        assertThatThrownBy(() -> service.delete(ID))
            .isInstanceOf(CouponAlreadyDeletedException.class);
    }

    @Test
    void delete_whenCouponDoesNotExist_throwsCouponNotFoundException() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ID))
            .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void delete_whenCouponAlreadyDeleted_propagatesCouponAlreadyDeletedException() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
        coupon.delete();
        when(repository.findById(ID)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.delete(ID))
            .isInstanceOf(CouponAlreadyDeletedException.class);
    }
}
