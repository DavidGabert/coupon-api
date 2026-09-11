package br.com.davidlopes.couponapi.domain.exception;

public class PastExpirationDateException extends RuntimeException {

    public PastExpirationDateException(String message) {
        super(message);
    }
}
