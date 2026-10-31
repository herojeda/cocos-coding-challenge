package com.cocoscap.challenge.application.paging;

/**
 * Offset page request. {@code page} is zero-based; {@code size} is between 1 and 100.
 */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    public int offset() {
        return page * size;
    }
}
