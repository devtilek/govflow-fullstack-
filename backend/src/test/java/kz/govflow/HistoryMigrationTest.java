package kz.govflow;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HistoryMigrationTest {
    @Autowired DataSource dataSource;

    @Test void upgradesExistingRequestsWithoutInventingEarlierTransitions() {
        // Isolate the upgrade test from the application's migrated schema.
        String schema = "history_upgrade_test";
        var jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("1").load().migrate();
            jdbc.update("INSERT INTO history_upgrade_test.service_requests "
                    + "(title, description, status, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                    "Existing new request", "Description", "NEW");
            jdbc.update("INSERT INTO history_upgrade_test.service_requests "
                    + "(title, description, status, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                    "Existing resolved request", "Description", "RESOLVED");

            var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            upgrade.migrate();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM history_upgrade_test.request_history", Long.class)).isEqualTo(2L);
            assertThat(jdbc.queryForList("SELECT action FROM history_upgrade_test.request_history", String.class))
                    .containsOnly("IMPORTED");
            assertThat(jdbc.queryForList("SELECT to_status FROM history_upgrade_test.request_history", String.class))
                    .containsExactlyInAnyOrder("NEW", "RESOLVED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM history_upgrade_test.request_history WHERE from_status IS NOT NULL", Long.class))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM history_upgrade_test.service_requests", Long.class)).isEqualTo(2L);
            upgrade.migrate();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM history_upgrade_test.request_history", Long.class)).isEqualTo(2L);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS history_upgrade_test CASCADE");
        }
    }
}
