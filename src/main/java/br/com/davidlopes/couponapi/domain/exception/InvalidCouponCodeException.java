package br.com.davidlopes.couponapi.domain.exception;

public class InvalidCouponCodeException extends RuntimeException {

    public InvalidCouponCodeException(String message) {
        super(message);
    }
}
