package com.cocoscap.challenge.architecture.fixtures.application;

import com.cocoscap.challenge.architecture.fixtures.adapter.out.persistence.PersistenceMarker;

public final class ApplicationDependingOnAdapter {

    private final PersistenceMarker persistence;

    public ApplicationDependingOnAdapter(PersistenceMarker persistence) {
        this.persistence = persistence;
    }

    public PersistenceMarker persistence() {
        return persistence;
    }
}
