package br.com.davidlopes.couponapi.application;

public class DuplicateCouponCodeException extends RuntimeException {

    public DuplicateCouponCodeException(String message) {
        super(message);
    }
}
