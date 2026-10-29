package com.cocoscap.challenge.architecture.fixtures.adapter.in.web;

import com.cocoscap.challenge.architecture.fixtures.adapter.out.persistence.PersistenceMarker;

public final class WebDependingOnPersistence {

    private final PersistenceMarker persistence;

    public WebDependingOnPersistence(PersistenceMarker persistence) {
        this.persistence = persistence;
    }

    public PersistenceMarker persistence() {
        return persistence;
    }
}
