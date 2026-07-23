package com.fajtech.sppogtfs.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BigQueryGtfsLoaderAdapterTest {

    @Test
    void cleanEventoStripsBrackets() {
        assertThat(BigQueryGtfsLoaderAdapter.cleanEvento("[desvio_tunel]")).isEqualTo("desvio_tunel");
        assertThat(BigQueryGtfsLoaderAdapter.cleanEvento("[via_praia_do_flamengo]"))
                .isEqualTo("via_praia_do_flamengo");
    }

    @Test
    void cleanEventoStripsQuotesAndWhitespace() {
        assertThat(BigQueryGtfsLoaderAdapter.cleanEvento("[ \"excepcionalidade\" ]"))
                .isEqualTo("excepcionalidade");
    }

    @Test
    void cleanEventoReturnsNullForNullOrEmpty() {
        assertThat(BigQueryGtfsLoaderAdapter.cleanEvento(null)).isNull();
        assertThat(BigQueryGtfsLoaderAdapter.cleanEvento("[]")).isNull();
        assertThat(BigQueryGtfsLoaderAdapter.cleanEvento("   ")).isNull();
    }

    @Test
    void cleanEventoLeavesPlainValueUntouched() {
        assertThat(BigQueryGtfsLoaderAdapter.cleanEvento("desvio_obras")).isEqualTo("desvio_obras");
    }
}
