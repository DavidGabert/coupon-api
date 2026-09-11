package br.com.davidlopes.couponapi.domain.exception;

public class CouponAlreadyDeletedException extends RuntimeException {

    public CouponAlreadyDeletedException(String message) {
        super(message);
    }
}
