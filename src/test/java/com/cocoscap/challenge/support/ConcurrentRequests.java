package com.cocoscap.challenge.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Runs two suppliers on real threads that start together (ADR-006 concurrency tests). */
public final class ConcurrentRequests {

    private ConcurrentRequests() {}

    public static <T> List<T> runTogether(Supplier<T> first, Supplier<T> second) throws Exception {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<T> firstResult = new AtomicReference<>();
        AtomicReference<T> secondResult = new AtomicReference<>();
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            ready.countDown();
            await(start);
            try {
                firstResult.set(first.get());
            } catch (Throwable ex) {
                firstError.set(ex);
            }
        });
        Thread t2 = new Thread(() -> {
            ready.countDown();
            await(start);
            try {
                secondResult.set(second.get());
            } catch (Throwable ex) {
                secondError.set(ex);
            }
        });

        t1.start();
        t2.start();
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        t1.join(15_000);
        t2.join(15_000);

        assertThat(firstError.get()).isNull();
        assertThat(secondError.get()).isNull();
        List<T> results = new ArrayList<>();
        results.add(firstResult.get());
        results.add(secondResult.get());
        assertThat(results).doesNotContainNull();
        return results;
    }

    static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
