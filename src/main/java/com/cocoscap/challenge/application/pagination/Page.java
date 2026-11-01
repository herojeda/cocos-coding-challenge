package com.cocoscap.challenge.application.pagination;

import java.util.List;

/** One page of results. {@code hasNext} is true when more items exist after this page. */
public record Page<T>(List<T> items, int number, int size, boolean hasNext) {

    public Page {
        if (items == null) {
            throw new IllegalArgumentException("items is required");
        }
        items = List.copyOf(items);
    }
}
