package com.cocoscap.challenge.adapter.out.persistence.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.instrument.InstrumentLookupPort;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentId;
import com.cocoscap.challenge.domain.instrument.InstrumentType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class JdbcInstrumentLookupAdapterIT {

    private static final InstrumentId PAMP = InstrumentId.of(47L);
    private static final InstrumentId BMA = InstrumentId.of(31L);
    private static final InstrumentId MISSING = InstrumentId.of(9_999_999L);

    @Autowired
    private InstrumentLookupPort instrumentLookupPort;

    @Test
    @DisplayName("findByIds returns matching instruments by primary key")
    void findByIdsReturnsMatchingInstruments() {
        Map<InstrumentId, Instrument> found = instrumentLookupPort.findByIds(Set.of(PAMP, BMA, MISSING));

        assertThat(found).containsOnlyKeys(PAMP, BMA);
        assertThat(found.get(PAMP))
                .isEqualTo(new Instrument(PAMP, "PAMP", "Pampa Holding S.A.", InstrumentType.STOCK));
        assertThat(found.get(BMA).ticker()).isEqualTo("BMA");
    }

    @Test
    @DisplayName("Empty instrument id collection returns empty map")
    void emptyIdsReturnEmptyMap() {
        assertThat(instrumentLookupPort.findByIds(List.of())).isEmpty();
    }
}
