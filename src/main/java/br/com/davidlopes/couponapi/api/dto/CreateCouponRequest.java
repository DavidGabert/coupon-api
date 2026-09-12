package br.com.davidlopes.couponapi.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateCouponRequest(
    @NotBlank String code,
    @NotBlank @Size(max = 255) String description,
    @NotNull BigDecimal discountValue,
    @NotNull LocalDateTime expirationDate,
    Boolean published
) {
}
