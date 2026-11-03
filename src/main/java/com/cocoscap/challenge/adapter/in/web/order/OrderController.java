package com.cocoscap.challenge.adapter.in.web.order;

import com.cocoscap.challenge.application.order.CancelOrderUseCase;
import com.cocoscap.challenge.application.order.PlaceOrderResult;
import com.cocoscap.challenge.application.order.PlaceOrderUseCase;
import com.cocoscap.challenge.domain.order.IdempotencyKey;
import com.cocoscap.challenge.domain.order.OrderId;
import com.cocoscap.challenge.domain.user.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/{userId}/orders")
@Tag(name = "Orders")
public class OrderController {

    static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final PlaceOrderUseCase placeOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    public OrderController(PlaceOrderUseCase placeOrderUseCase, CancelOrderUseCase cancelOrderUseCase) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Place an order",
            description =
                    "Requires Idempotency-Key (1–64 printable ASCII; UUID recommended). "
                            + "size and amount are mutually exclusive; price only for LIMIT. "
                            + "REJECTED orders still return 201.")
    @ApiResponse(
            responseCode = "201",
            description = "Order registered (including REJECTED)",
            content = @Content(schema = @Schema(implementation = OrderResponse.class)))
    @ApiResponse(
            responseCode = "200",
            description = "Idempotent replay",
            headers =
                    @Header(
                            name = IDEMPOTENT_REPLAYED_HEADER,
                            description = "Always true on replay",
                            schema = @Schema(type = "string", allowableValues = "true")),
            content = @Content(schema = @Schema(implementation = OrderResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR, MALFORMED_REQUEST",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(
            responseCode = "404",
            description = "USER_NOT_FOUND, INSTRUMENT_NOT_FOUND",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(
            responseCode = "409",
            description = "ACCOUNT_BUSY",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(
            responseCode = "422",
            description = "IDEMPOTENCY_KEY_REUSED",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    ResponseEntity<OrderResponse> place(
            @PathVariable @Min(1) long userId,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "1–64 printable ASCII; UUID recommended",
                            schema = @Schema(minLength = 1, maxLength = 64))
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResult result = placeOrderUseCase.execute(
                request.toNewOrder(UserId.of(userId)), IdempotencyKey.of(idempotencyKey));
        OrderResponse body = OrderResponse.from(result.order());
        return switch (result) {
            case PlaceOrderResult.Registered ignored ->
                ResponseEntity.status(HttpStatus.CREATED).body(body);
            case PlaceOrderResult.Replayed ignored ->
                ResponseEntity.ok().header(IDEMPOTENT_REPLAYED_HEADER, "true").body(body);
        };
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order", description = "Only NEW orders can be cancelled.")
    @ApiResponse(
            responseCode = "200",
            description = "Order cancelled",
            content = @Content(schema = @Schema(implementation = OrderResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(
            responseCode = "404",
            description = "USER_NOT_FOUND, ORDER_NOT_FOUND",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    @ApiResponse(
            responseCode = "409",
            description = "ORDER_NOT_CANCELLABLE (current_status), ACCOUNT_BUSY",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
    ResponseEntity<OrderResponse> cancel(
            @PathVariable @Min(1) long userId, @PathVariable @Min(1) long orderId) {
        return ResponseEntity.ok(OrderResponse.from(
                cancelOrderUseCase.execute(UserId.of(userId), OrderId.of(orderId))));
    }
}
