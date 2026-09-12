package br.com.davidlopes.couponapi.domain.exception;

public class InvalidDescriptionException extends RuntimeException {

    public InvalidDescriptionException(String message) {
        super(message);
    }
}
