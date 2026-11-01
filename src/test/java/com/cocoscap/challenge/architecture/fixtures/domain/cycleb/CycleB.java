package com.cocoscap.challenge.architecture.fixtures.domain.cycleb;

import com.cocoscap.challenge.architecture.fixtures.domain.cyclea.CycleA;

public final class CycleB {

    public CycleA next() {
        return new CycleA();
    }
}
