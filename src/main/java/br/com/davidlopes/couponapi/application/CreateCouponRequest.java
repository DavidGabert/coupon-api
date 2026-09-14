package br.com.davidlopes.couponapi.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Input shape for {@link CouponService#create}. Lives in the application layer, not {@code api}:
 * {@code CouponController} depends on this type, not the other way around, so the service never
 * needs to import anything from {@code api}.
 */
public record CreateCouponRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 255) String description,
    @NotNull BigDecimal discountValue,
    @NotNull Instant expirationDate,
    Boolean published
) {
}
