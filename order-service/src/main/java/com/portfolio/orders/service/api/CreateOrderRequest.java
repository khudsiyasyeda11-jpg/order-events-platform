package com.portfolio.orders.service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request to place a new order")
public record CreateOrderRequest(
        @Schema(example = "CUST-1001")
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "must be alphanumeric or '-'")
        String customerId,

        @Schema(example = "USD")
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO-4217 code")
        String currency,

        @NotEmpty @Size(max = 50) @Valid
        List<OrderItemRequest> items) {
}
