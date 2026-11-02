package com.cocoscap.challenge.domain.order;

/**
 * Malformed placement pedido (S8). The web layer maps this to {@code VALIDATION_ERROR} with
 * {@link #field()}.
 */
public final class NewOrderValidationException extends RuntimeException {

    private final String field;

    public NewOrderValidationException(String field, String message) {
        super(message);
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        this.field = field;
    }

    public String field() {
        return field;
    }
}
