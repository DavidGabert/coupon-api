package br.com.davidlopes.couponapi.api;

import br.com.davidlopes.couponapi.api.dto.CouponResponse;
import br.com.davidlopes.couponapi.api.dto.CreateCouponRequest;
import br.com.davidlopes.couponapi.application.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/coupon")
@Tag(name = "Coupon", description = "Create, read and soft-delete discount coupons")
public class CouponController {

    private static final String ERROR_SCHEMA = "ErrorResponse";

    private final CouponService service;

    public CouponController(CouponService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
        summary = "Create a coupon",
        description = "Validates the coupon code, discount value and expiration date, then stores the coupon. "
            + "The discount value is rounded to two decimal places. The code must not be in use by another "
            + "active coupon.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Coupon created"),
        @ApiResponse(responseCode = "400", description = "Request body failed validation or a business rule",
            content = @Content(schema = @Schema(name = ERROR_SCHEMA, implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Code already in use by an active coupon",
            content = @Content(schema = @Schema(name = ERROR_SCHEMA, implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CreateCouponRequest request) {
        CouponResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get a coupon by id",
        description = "Returns the coupon with the given id, including soft-deleted ones — "
            + "the response's status field reflects whether it was deleted.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Coupon found"),
        @ApiResponse(responseCode = "400", description = "Id is not a valid UUID",
            content = @Content(schema = @Schema(name = ERROR_SCHEMA, implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No coupon with that id",
            content = @Content(schema = @Schema(name = ERROR_SCHEMA, implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CouponResponse> findById(
            @Parameter(description = "Id of the coupon") @PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a coupon",
        description = "Soft-deletes the coupon: it is marked inactive and its code is released for reuse. "
            + "Deleting an already-deleted coupon is rejected.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Coupon deleted"),
        @ApiResponse(responseCode = "400", description = "Id is not a valid UUID",
            content = @Content(schema = @Schema(name = ERROR_SCHEMA, implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No coupon with that id",
            content = @Content(schema = @Schema(name = ERROR_SCHEMA, implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Coupon was already deleted",
            content = @Content(schema = @Schema(name = ERROR_SCHEMA, implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Id of the coupon") @PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
