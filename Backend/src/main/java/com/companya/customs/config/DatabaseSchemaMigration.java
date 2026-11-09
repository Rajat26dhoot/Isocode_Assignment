package com.companya.customs.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Converts legacy MySQL ENUM columns to VARCHAR once. This is needed because
 * Hibernate's update mode does not add new enum values to an existing MySQL
 * ENUM column.
 */
@Configuration
@RequiredArgsConstructor
public class DatabaseSchemaMigration {
    private final JdbcTemplate jdbc;

    @Bean
    CommandLineRunner migrateLegacyEnumColumns() {
        return args -> {
            migrateEnumColumn("shipments", "status", "VARCHAR(32) NOT NULL");

            // Existing databases may still have these columns as MySQL ENUMs from
            // the original schema. Hibernate does not widen an ENUM when a new
            // Java enum value is added, so a newly generated discrepancy can fail
            // the whole extraction transaction with "Data truncated".
            migrateEnumColumn("cross_check_discrepancies", "rule_code", "VARCHAR(64) NOT NULL");
            migrateEnumColumn("cross_check_discrepancies", "severity", "VARCHAR(16) NOT NULL");
            migrateEnumColumn("cross_check_discrepancies", "status", "VARCHAR(32) NOT NULL");
        };
    }

    private void migrateEnumColumn(String table, String column, String definition) {
        Integer columnCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                table,
                column
        );
        if (columnCount == null || columnCount == 0) return;

        String dataType = jdbc.queryForObject(
                "SELECT DATA_TYPE FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                String.class,
                table,
                column
        );
        if ("enum".equalsIgnoreCase(dataType)) {
            jdbc.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + definition);
        }
    }
}
