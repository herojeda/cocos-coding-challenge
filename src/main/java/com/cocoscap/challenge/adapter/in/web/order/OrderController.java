package com.cocoscap.challenge.adapter.in.web.order;

import com.cocoscap.challenge.application.order.PlaceOrderResult;
import com.cocoscap.challenge.application.order.PlaceOrderUseCase;
import com.cocoscap.challenge.domain.order.IdempotencyKey;
import com.cocoscap.challenge.domain.user.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/{userId}/orders")
public class OrderController {

    static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    private final PlaceOrderUseCase placeOrderUseCase;

    public OrderController(PlaceOrderUseCase placeOrderUseCase) {
        this.placeOrderUseCase = placeOrderUseCase;
    }

    @PostMapping
    ResponseEntity<PlaceOrderResponse> place(
            @PathVariable @Min(1) long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResult result = placeOrderUseCase.execute(
                request.toNewOrder(UserId.of(userId)), IdempotencyKey.of(idempotencyKey));
        PlaceOrderResponse body = PlaceOrderResponse.from(result.order());
        return switch (result) {
            case PlaceOrderResult.Registered ignored ->
                ResponseEntity.status(HttpStatus.CREATED).body(body);
            case PlaceOrderResult.Replayed ignored ->
                ResponseEntity.ok().header(IDEMPOTENT_REPLAYED_HEADER, "true").body(body);
        };
    }
}
