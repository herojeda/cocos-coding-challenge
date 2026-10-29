package com.cocoscap.challenge.architecture.fixtures.application;

import com.cocoscap.challenge.architecture.fixtures.domain.CompliantDomainType;
import org.springframework.transaction.annotation.Transactional;

public final class CompliantTransactionalUseCase {

    @Transactional
    public CompliantDomainType execute(String name) {
        return new CompliantDomainType(name);
    }
}
