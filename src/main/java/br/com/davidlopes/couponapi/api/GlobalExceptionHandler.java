package br.com.davidlopes.couponapi.api;

import br.com.davidlopes.couponapi.application.CouponNotFoundException;
import br.com.davidlopes.couponapi.application.DuplicateCouponCodeException;
import br.com.davidlopes.couponapi.domain.exception.CouponAlreadyDeletedException;
import br.com.davidlopes.couponapi.domain.exception.InvalidCouponCodeException;
import br.com.davidlopes.couponapi.domain.exception.InvalidDiscountValueException;
import br.com.davidlopes.couponapi.domain.exception.PastExpirationDateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Extends {@link ResponseEntityExceptionHandler} so the standard Spring MVC exceptions
 * (unreadable body, unsupported media type, unsupported method, unknown path, ...) keep
 * their correct HTTP status instead of being swallowed by the {@code Exception} catch-all
 * and reported as 500. Their bodies are rewritten to this API's {@link ErrorResponse}
 * shape by overriding {@link #handleExceptionInternal}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCouponCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCouponCode(InvalidCouponCodeException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidDiscountValueException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDiscountValue(InvalidDiscountValueException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PastExpirationDateException.class)
    public ResponseEntity<ErrorResponse> handlePastExpirationDate(PastExpirationDateException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ExceptionHandler(CouponNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(CouponNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CouponAlreadyDeletedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyDeleted(CouponAlreadyDeletedException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DuplicateCouponCodeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateCouponCodeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    /**
     * Bean-validation failures on the request body. Overridden (rather than declared as a
     * second {@code @ExceptionHandler}) because the base class already maps
     * {@link MethodArgumentNotValidException}, which would be an ambiguous mapping.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return handleExceptionInternal(ex, errorBody(status, message), headers, status, request);
    }

    /**
     * Malformed or type-incompatible JSON. The raw parser message is replaced with a stable,
     * non-leaky description of the problem.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        return handleExceptionInternal(ex, errorBody(status, "Malformed request body"), headers, status, request);
    }

    /**
     * Single funnel for every exception the base class handles: replaces Spring's default
     * {@code ProblemDetail} body with this API's standardized {@link ErrorResponse}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        Object responseBody = (body instanceof ErrorResponse) ? body : errorBody(statusCode, ex.getMessage());
        return super.handleExceptionInternal(ex, responseBody, headers, statusCode, request);
    }

    private ErrorResponse errorBody(HttpStatusCode statusCode, String message) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String reason = (status != null) ? status.getReasonPhrase() : "Error";
        return new ErrorResponse(statusCode.value(), reason, message, LocalDateTime.now());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(errorBody(status, message));
    }
}
