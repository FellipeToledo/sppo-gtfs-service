package com.fajtech.sppogtfs.infrastructure.adapter.out.persistence;

import com.fajtech.sppogtfs.application.port.out.GtfsLoaderPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JPA persistence adapter maps a real Postgres GTFS schema into the loader
 * port's raw records. Uses Testcontainers with the mini fixture.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaGtfsLoaderAdapter.class)
@Sql(scripts = "classpath:gtfs-fixture.sql")
@Testcontainers
class JpaGtfsLoaderAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    JpaGtfsLoaderAdapter adapter;

    @Test
    void loadsRoutes() {
        assertThat(adapter.loadRoutes())
                .extracting(GtfsLoaderPort.RawRoute::routeShortName)
                .contains("100", "485", "BRT1");
    }

    @Test
    void loadsOnlyTripsWithShape() {
        assertThat(adapter.loadTrips())
                .allSatisfy(t -> assertThat(t.shapeId()).isNotNull())
                .hasSize(5);
    }

    @Test
    void loadsShapePoints() {
        assertThat(adapter.loadShapePoints()).hasSize(10);
    }

    @Test
    void loadsFeedInfo() {
        assertThat(adapter.loadFeedInfo()).isPresent()
                .get()
                .satisfies(fi -> {
                    assertThat(fi.feedVersion()).isEqualTo("2026-07");
                    assertThat(fi.publisherName()).isEqualTo("SMTR");
                    assertThat(fi.startDate()).isNotNull();
                });
    }
}
