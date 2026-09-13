package br.com.davidlopes.couponapi.api.dto;

import br.com.davidlopes.couponapi.domain.Coupon;
import br.com.davidlopes.couponapi.domain.CouponStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponResponse(
    UUID id,
    String code,
    String description,
    BigDecimal discountValue,
    Instant expirationDate,
    CouponStatus status,
    boolean published,
    boolean redeemed
) implements Serializable {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
            coupon.getId(),
            coupon.getCode().value(),
            coupon.getDescription(),
            coupon.getDiscountValue(),
            coupon.getExpirationDate(),
            coupon.status(),
            coupon.isPublished(),
            coupon.isRedeemed()
        );
    }
}
