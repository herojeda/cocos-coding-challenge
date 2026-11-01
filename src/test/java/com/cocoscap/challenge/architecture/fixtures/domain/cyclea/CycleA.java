package com.cocoscap.challenge.architecture.fixtures.domain.cyclea;

import com.cocoscap.challenge.architecture.fixtures.domain.cycleb.CycleB;

public final class CycleA {

    public CycleB next() {
        return new CycleB();
    }
}
