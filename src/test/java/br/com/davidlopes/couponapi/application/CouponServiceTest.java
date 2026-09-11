package br.com.davidlopes.couponapi.application;

import br.com.davidlopes.couponapi.api.dto.CouponResponse;
import br.com.davidlopes.couponapi.api.dto.CreateCouponRequest;
import br.com.davidlopes.couponapi.domain.Coupon;
import br.com.davidlopes.couponapi.domain.exception.CouponAlreadyDeletedException;
import br.com.davidlopes.couponapi.infrastructure.CouponJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponJpaRepository repository;

    @InjectMocks
    private CouponService service;

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(30);

    private CreateCouponRequest validRequest() {
        return new CreateCouponRequest("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
    }

    @Test
    void create_withNoExistingCode_savesAndReturnsResponse() {
        when(repository.existsByActiveCode("AB12CD")).thenReturn(false);
        when(repository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponResponse response = service.create(validRequest());

        assertThat(response.code()).isEqualTo("AB12CD");
        assertThat(response.active()).isTrue();
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
        when(repository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(coupon));

        CouponResponse response = service.findById(1L);

        assertThat(response.code()).isEqualTo("AB12CD");
    }

    @Test
    void findById_whenNotFound_throwsCouponNotFoundException() {
        when(repository.findByIdAndActiveTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(1L))
            .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void findAll_mapsAllActiveCouponsToResponses() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
        when(repository.findAllByActiveTrue()).thenReturn(List.of(coupon));

        List<CouponResponse> responses = service.findAll();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).code()).isEqualTo("AB12CD");
    }

    @Test
    void delete_whenCouponExists_marksItDeletedAndSaves() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
        when(repository.findById(1L)).thenReturn(Optional.of(coupon));

        service.delete(1L);

        assertThat(coupon.isActive()).isFalse();
        verify(repository).save(coupon);
    }

    @Test
    void delete_whenCouponDoesNotExist_throwsCouponNotFoundException() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void delete_whenCouponAlreadyDeleted_propagatesCouponAlreadyDeletedException() {
        Coupon coupon = Coupon.create("AB12CD", "desc", new BigDecimal("10.00"), FUTURE, false);
        coupon.delete();
        when(repository.findById(1L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(CouponAlreadyDeletedException.class);
    }
}
