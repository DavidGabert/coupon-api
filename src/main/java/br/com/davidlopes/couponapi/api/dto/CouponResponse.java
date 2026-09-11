package br.com.davidlopes.couponapi.api.dto;

import br.com.davidlopes.couponapi.domain.Coupon;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponResponse(
    Long id,
    String code,
    String description,
    BigDecimal discountValue,
    LocalDateTime expirationDate,
    boolean published,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime deletedAt
) implements Serializable {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
            coupon.getId(),
            coupon.getCode().value(),
            coupon.getDescription(),
            coupon.getDiscountValue(),
            coupon.getExpirationDate(),
            coupon.isPublished(),
            coupon.isActive(),
            coupon.getCreatedAt(),
            coupon.getDeletedAt()
        );
    }
}
