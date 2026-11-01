package com.cocoscap.challenge.adapter.out.persistence.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocoscap.challenge.PostgresTestcontainersConfiguration;
import com.cocoscap.challenge.application.instrument.SearchInstrumentsUseCase;
import com.cocoscap.challenge.application.pagination.Page;
import com.cocoscap.challenge.application.pagination.Pagination;
import com.cocoscap.challenge.domain.instrument.Instrument;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchCriteria;
import com.cocoscap.challenge.domain.instrument.InstrumentSearchText;
import com.cocoscap.challenge.domain.instrument.InstrumentSort;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class JdbcInstrumentSearchAdapterIT {

    @Autowired
    private SearchInstrumentsUseCase searchInstruments;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("AS-01 · Filtro por ticker exacto")
    void as01() {
        assertThat(tickers(byTicker("GGAL"))).contains("GGAL");
        assertThat(byTicker("GGAL").items())
                .filteredOn(i -> i.ticker().equals("GGAL"))
                .singleElement()
                .extracting(Instrument::name)
                .isEqualTo("Grupo Financiero Galicia");
    }

    @Test
    @DisplayName("AS-02 · Filtro por parte del ticker")
    void as02() {
        assertThat(byTicker("tgs").items())
                .filteredOn(i -> i.ticker().equals("TGSU2"))
                .singleElement()
                .satisfies(i -> assertThat(i.name()).isEqualTo("Transportadora de Gas del Sur"));
    }

    @Test
    @DisplayName("AS-03 · Filtro por parte del nombre")
    void as03() {
        assertThat(tickers(byName("banco"))).containsExactly("BBAR", "BHIP", "BMA", "BPAT");
    }

    @Test
    @DisplayName("AS-04 · Filtro por nombre sin tildes")
    void as04() {
        assertThat(tickers(byName("rio de la plata"))).contains("MOLI");
    }

    @Test
    @DisplayName("AS-05 · Búsqueda de los pesos")
    void as05() {
        assertThat(byName("pesos").items()).isEmpty();
        assertThat(byTicker("ars").items()).isEmpty();
    }

    @Test
    @DisplayName("AS-06 · Datos de un instrumento encontrado")
    void as06() {
        assertThat(byTicker("YPFD").items()).singleElement().satisfies(i -> {
            assertThat(i.ticker()).isEqualTo("YPFD");
            assertThat(i.name()).isEqualTo("Y.P.F. S.A.");
            assertThat(i.type().name()).isEqualTo("STOCK");
            assertThat(i.id().value()).isPositive();
        });
    }

    @Test
    @Transactional
    @DisplayName("AS-07 · Relevancia sobre el ticker")
    void as07() {
        jdbcClient
                .sql("INSERT INTO instruments (ticker, name, type) VALUES ('PAT', 'Pat Test', 'STOCK')")
                .update();
        assertThat(tickers(byTicker("pat"))).containsExactly("PAT", "PATA", "BPAT");
    }

    @Test
    @DisplayName("AS-09 · Búsqueda sin coincidencias")
    void as09() {
        Page<Instrument> page = byTicker("zzz");
        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("AS-10 · Texto con espacios alrededor")
    void as10() {
        assertThat(tickers(byName("  rio de la plata  "))).contains("MOLI");
    }

    @Test
    @DisplayName("AS-11 · Caracteres que no son comodines")
    void as11() {
        assertThat(byTicker("%").items()).isEmpty();
        assertThat(byName("_").items()).isEmpty();
    }

    @Test
    @DisplayName("AS-13 · Páginas siguientes")
    void as13() {
        Page<Instrument> first = search(name("banco"), page(0, 2));
        Page<Instrument> second = search(name("banco"), page(1, 2));
        assertThat(tickers(first)).containsExactly("BBAR", "BHIP");
        assertThat(first.hasNext()).isTrue();
        assertThat(tickers(second)).containsExactly("BMA", "BPAT");
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("AS-14 · Página posterior a la última")
    void as14() {
        Page<Instrument> page = search(name("banco"), page(2, 2));
        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("AS-14 · Página posterior a la última (offset mayor que int)")
    void as14_overflow() {
        Page<Instrument> page = search(name("banco"), page(Integer.MAX_VALUE, 100));
        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @ParameterizedTest(name = "{0} / ticker={1} / name={2}")
    @MethodSource("filterAndSortCombinations")
    void everyFilterAndSortCombinationExecutes(InstrumentSort sort, boolean withTicker, boolean withName) {
        Optional<InstrumentSearchText> ticker =
                withTicker ? Optional.of(new InstrumentSearchText("a")) : Optional.empty();
        Optional<InstrumentSearchText> name =
                withName ? Optional.of(new InstrumentSearchText("a")) : Optional.empty();
        Page<Instrument> page =
                search(new InstrumentSearchCriteria(ticker, name, sort), page(0, Pagination.DEFAULT_SIZE));
        assertThat(page.items()).isNotNull();
    }

    static Stream<Arguments> filterAndSortCombinations() {
        return Stream.of(InstrumentSort.values())
                .flatMap(sort -> Stream.of(
                        Arguments.of(sort, false, false),
                        Arguments.of(sort, true, false),
                        Arguments.of(sort, false, true),
                        Arguments.of(sort, true, true)));
    }

    @Test
    @DisplayName("AS-17 · El filtro de ticker no busca en el nombre")
    void as17() {
        assertThat(byTicker("banco").items()).isEmpty();
    }

    @Test
    @DisplayName("AS-18 · El filtro de nombre no busca en el ticker")
    void as18() {
        assertThat(byName("bma").items()).isEmpty();
    }

    @Test
    @DisplayName("AS-19 · Ticker y nombre juntos")
    void as19() {
        var criteria = new InstrumentSearchCriteria(
                Optional.of(new InstrumentSearchText("mol")),
                Optional.of(new InstrumentSearchText("molinos")),
                InstrumentSort.RELEVANCE);
        assertThat(tickers(search(criteria, page(0, 20)))).containsExactly("MOLA", "MOLI");
    }

    @Test
    @DisplayName("AS-20 · Sin filtros")
    void as20() {
        Page<Instrument> page = search(InstrumentSearchCriteria.unfiltered(), page(0, 100));
        assertThat(page.items()).hasSize(65);
        assertThat(tickers(page)).doesNotContain("ARS");
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("AS-21 · Relevancia sobre el nombre")
    void as21() {
        assertThat(tickers(byName("gas"))).containsExactly("GBAN", "CGPA2", "DGCU2", "METR", "TGNO4", "TGSU2");
    }

    @Test
    @DisplayName("AS-22 · Orden por ticker")
    void as22() {
        var criteria = new InstrumentSearchCriteria(
                Optional.empty(), Optional.of(new InstrumentSearchText("grupo")), InstrumentSort.TICKER);
        assertThat(tickers(search(criteria, page(0, 20)))).containsExactly("GCLA", "GGAL", "OEST", "SUPV");
    }

    @Test
    @DisplayName("AS-23 · Orden por nombre")
    void as23() {
        var criteria = new InstrumentSearchCriteria(
                Optional.empty(), Optional.of(new InstrumentSearchText("gas")), InstrumentSort.NAME);
        assertThat(tickers(search(criteria, page(0, 20))))
                .containsExactly("CGPA2", "DGCU2", "GBAN", "METR", "TGNO4", "TGSU2");
    }

    private Page<Instrument> byTicker(String value) {
        return search(ticker(value), page(0, Pagination.DEFAULT_SIZE));
    }

    private Page<Instrument> byName(String value) {
        return search(name(value), page(0, Pagination.DEFAULT_SIZE));
    }

    private Page<Instrument> search(InstrumentSearchCriteria criteria, Pagination pagination) {
        return searchInstruments.execute(criteria, pagination);
    }

    private static InstrumentSearchCriteria ticker(String value) {
        return new InstrumentSearchCriteria(
                Optional.of(new InstrumentSearchText(value)), Optional.empty(), InstrumentSort.RELEVANCE);
    }

    private static InstrumentSearchCriteria name(String value) {
        return new InstrumentSearchCriteria(
                Optional.empty(), Optional.of(new InstrumentSearchText(value)), InstrumentSort.RELEVANCE);
    }

    private static Pagination page(int number, int size) {
        return new Pagination(number, size);
    }

    private static List<String> tickers(Page<Instrument> page) {
        return page.items().stream().map(Instrument::ticker).toList();
    }
}
