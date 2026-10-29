package com.cocoscap.challenge.adapter.in.web.error;

import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

/**
 * Closed catalog of API problem codes from ADR-007.
 */
public enum ProblemCode {

    MALFORMED_REQUEST(
            HttpStatus.BAD_REQUEST,
            "Malformed request",
            "The request body could not be read"),
    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "Validation error",
            "The request does not satisfy validation constraints"),
    UNSUPPORTED_REQUEST(
            null,
            "Unsupported request",
            "The request path, method, or media type is not supported"),
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "User not found",
            "The requested user does not exist"),
    INSTRUMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Instrument not found",
            "The requested instrument does not exist"),
    ORDER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Order not found",
            "The requested order does not exist"),
    ORDER_NOT_CANCELLABLE(
            HttpStatus.CONFLICT,
            "Order cannot be cancelled",
            "Only orders in status NEW can be cancelled"),
    IDEMPOTENCY_KEY_REUSED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Idempotency key reused",
            "The idempotency key was already used with a different request"),
    ACCOUNT_BUSY(
            HttpStatus.CONFLICT,
            "Account busy",
            "The account is busy; retry the request"),
    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal error",
            "An unexpected error occurred");

    private final @Nullable HttpStatus status;
    private final String title;
    private final String detail;

    ProblemCode(@Nullable HttpStatus status, String title, String detail) {
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public String typeUri() {
        return "urn:cocos-challenge:problem:" + name().toLowerCase().replace('_', '-');
    }

    public @Nullable HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }
}
