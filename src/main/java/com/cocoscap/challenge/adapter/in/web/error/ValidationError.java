package com.cocoscap.challenge.adapter.in.web.error;

/**
 * One field-level validation failure exposed in a {@link ProblemCode#VALIDATION_ERROR} response.
 */
public record ValidationError(String field, String reason) {
}
