package br.com.davidlopes.couponapi.application;

public class CouponNotFoundException extends RuntimeException {

    public CouponNotFoundException(String message) {
        super(message);
    }
}
