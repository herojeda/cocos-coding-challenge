package com.cocoscap.challenge.application.pagination;

/** Zero-based offset pagination; {@code size} is between 1 and 100. */
public record Pagination(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public Pagination {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    public long offset() {
        return Math.multiplyExact((long) page, size);
    }
}
