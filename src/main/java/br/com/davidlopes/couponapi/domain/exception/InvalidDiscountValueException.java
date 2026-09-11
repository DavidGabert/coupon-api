package br.com.davidlopes.couponapi.domain.exception;

public class InvalidDiscountValueException extends RuntimeException {

    public InvalidDiscountValueException(String message) {
        super(message);
    }
}
