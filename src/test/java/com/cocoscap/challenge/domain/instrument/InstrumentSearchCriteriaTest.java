package com.cocoscap.challenge.domain.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class InstrumentSearchCriteriaTest {

    @Test
    void acceptsBothFiltersEmpty() {
        InstrumentSearchCriteria criteria = InstrumentSearchCriteria.unfiltered();

        assertThat(criteria.ticker()).isEmpty();
        assertThat(criteria.name()).isEmpty();
        assertThat(criteria.sort()).isEqualTo(InstrumentSort.TICKER);
    }

    @Test
    void rejectsNullComponents() {
        assertThatThrownBy(() -> new InstrumentSearchCriteria(null, Optional.empty(), InstrumentSort.TICKER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstrumentSearchCriteria(Optional.empty(), null, InstrumentSort.TICKER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstrumentSearchCriteria(Optional.empty(), Optional.empty(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
