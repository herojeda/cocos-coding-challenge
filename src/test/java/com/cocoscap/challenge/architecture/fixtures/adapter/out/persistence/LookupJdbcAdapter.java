package com.cocoscap.challenge.architecture.fixtures.adapter.out.persistence;

import com.cocoscap.challenge.architecture.fixtures.application.LookupPort;

public final class LookupJdbcAdapter implements LookupPort {

    @Override
    public String lookup(String key) {
        return key;
    }
}
