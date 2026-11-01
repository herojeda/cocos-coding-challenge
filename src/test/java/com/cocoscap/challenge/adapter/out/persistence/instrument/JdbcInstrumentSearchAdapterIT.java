package com.cocoscap.challenge.adapter.out.persistence.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.instrument.SearchInstrumentsUseCase;
import com.cocoscap.challenge.application.pagination.Page;
import com.cocoscap.challenge.application.pagination.Pagination;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchText;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class JdbcInstrumentSearchAdapterIT {

    @Autowired
    private SearchInstrumentsUseCase searchInstruments;

    @Test
    @DisplayName("AS-01 · Búsqueda por ticker exacto")
    void as01_exactTicker() {
        assertThat(tickers(search("GGAL"))).contains("GGAL");
        assertThat(search("GGAL").items())
                .filteredOn(i -> i.ticker().equals("GGAL"))
                .singleElement()
                .extracting(Instrument::name)
                .isEqualTo("Grupo Financiero Galicia");
    }

    @Test
    @DisplayName("AS-02 · Búsqueda por parte del ticker")
    void as02_partialTicker() {
        assertThat(search("tgs").items())
                .filteredOn(i -> i.ticker().equals("TGSU2"))
                .singleElement()
                .satisfies(i -> assertThat(i.name()).isEqualTo("Transportadora de Gas del Sur"));
    }

    @Test
    @DisplayName("AS-03 · Búsqueda por parte del nombre")
    void as03_partialName() {
        assertThat(tickers(search("banco"))).containsExactly("BBAR", "BHIP", "BMA", "BPAT");
    }

    @Test
    @DisplayName("AS-04 · Búsqueda sin tildes")
    void as04_withoutAccents() {
        assertThat(tickers(search("rio de la plata"))).contains("MOLI");
    }

    @Test
    @DisplayName("AS-05 · Búsqueda de los pesos")
    void as05_excludesCurrency() {
        assertThat(search("pesos").items()).isEmpty();
    }

    @Test
    @DisplayName("AS-07 · Coincidencia exacta de ticker primero")
    void as07_exactTickerFirst() {
        assertThat(tickers(search("moli"))).containsExactly("MOLI", "MOLA", "SEMI");
    }

    @Test
    @DisplayName("AS-09 · Búsqueda sin coincidencias")
    void as09_noMatches() {
        Page<Instrument> page = search("zzz");
        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("AS-10 · Texto con espacios alrededor")
    void as10_trimmedText() {
        assertThat(tickers(searchInstruments.execute(
                        new InstrumentSearchText("  moli  "), new Pagination(0, Pagination.DEFAULT_SIZE))))
                .containsExactly("MOLI", "MOLA", "SEMI");
    }

    @Test
    @DisplayName("AS-11 · Caracteres que no son comodines")
    void as11_literalWildcards() {
        assertThat(search("%").items()).isEmpty();
        assertThat(search("_").items()).isEmpty();
    }

    @Test
    @DisplayName("AS-13 · Páginas siguientes")
    void as13_followingPages() {
        Page<Instrument> first =
                searchInstruments.execute(new InstrumentSearchText("banco"), new Pagination(0, 2));
        Page<Instrument> second =
                searchInstruments.execute(new InstrumentSearchText("banco"), new Pagination(1, 2));

        assertThat(tickers(first)).containsExactly("BBAR", "BHIP");
        assertThat(first.hasNext()).isTrue();
        assertThat(tickers(second)).containsExactly("BMA", "BPAT");
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("AS-14 · Página posterior a la última")
    void as14_pastLastPage() {
        Page<Instrument> page =
                searchInstruments.execute(new InstrumentSearchText("banco"), new Pagination(2, 2));

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("AS-14 · Página con offset que desborda int")
    void as14_largePageOffsetDoesNotFail() {
        Page<Instrument> page = searchInstruments.execute(
                new InstrumentSearchText("banco"), new Pagination(Integer.MAX_VALUE, 100));

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    private Page<Instrument> search(String text) {
        return searchInstruments.execute(new InstrumentSearchText(text), new Pagination(0, Pagination.DEFAULT_SIZE));
    }

    private static List<String> tickers(Page<Instrument> page) {
        return page.items().stream().map(Instrument::ticker).toList();
    }
}
