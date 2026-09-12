package br.com.davidlopes.couponapi.api;

import br.com.davidlopes.couponapi.domain.exception.InvalidDescriptionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test for the handlers that can never be reached over HTTP through the normal
 * DTO path. {@code CreateCouponRequest.description} carries {@code @Size(max = 255)}, so a
 * too-long description is always rejected by Bean Validation before {@code Coupon.create()}'s
 * own length check ever runs — meaning {@link InvalidDescriptionException} (and therefore
 * {@link GlobalExceptionHandler#handleInvalidDescription}) is unreachable via
 * {@code CouponControllerErrorHandlingTest}'s MockMvc requests. The domain check still matters
 * as a safety net for any non-HTTP caller of {@code Coupon.create()}, so this test calls the
 * handler directly, the same way it would run if that safety net ever actually fired.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleInvalidDescription_returns400WithStandardBody() {
        InvalidDescriptionException exception = new InvalidDescriptionException("description must not be blank");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidDescription(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).isEqualTo("description must not be blank");
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
