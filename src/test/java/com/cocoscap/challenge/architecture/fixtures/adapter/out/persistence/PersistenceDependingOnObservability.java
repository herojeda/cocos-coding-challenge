package com.cocoscap.challenge.architecture.fixtures.adapter.out.persistence;

import com.cocoscap.challenge.architecture.fixtures.adapter.out.observability.ObservabilityMarker;

public final class PersistenceDependingOnObservability {

    private final ObservabilityMarker observability;

    public PersistenceDependingOnObservability(ObservabilityMarker observability) {
        this.observability = observability;
    }

    public ObservabilityMarker observability() {
        return observability;
    }
}
