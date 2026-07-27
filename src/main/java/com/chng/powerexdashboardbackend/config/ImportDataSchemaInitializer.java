package com.chng.powerexdashboardbackend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class ImportDataSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("sql/import-data-schema.sql"));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
        ensureImportJobFilesColumn("duplicate_rows", "INT NOT NULL DEFAULT 0");
        ensureImportJobFilesColumn("new_rows", "INT NOT NULL DEFAULT 0");
        ensureImportJobFilesColumn("updated_rows", "INT NOT NULL DEFAULT 0");
        ensureColumnType("spot_transactions", "longterm_percent", "DECIMAL(18,6) NULL");
        ensureColumnType("import_job_spot_rows", "longterm_percent", "DECIMAL(18,6) NULL");
        ensureColumnType("import_version_spot_snapshot", "longterm_percent", "DECIMAL(18,6) NULL");
        ensureImportVersionColumn("longterm_transactions");
        ensureImportVersionColumn("spot_transactions");
    }

    private void ensureImportJobFilesColumn(String columnName, String definition) {
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'import_job_files'
                  AND COLUMN_NAME = ?
                """, Integer.class, columnName);
        if (columnCount != null && columnCount > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE import_job_files ADD COLUMN " + columnName + " " + definition);
    }

    private void ensureImportVersionColumn(String tableName) {
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = 'import_version_id'
                """, Integer.class, tableName);
        if (columnCount != null && columnCount > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN import_version_id BIGINT NULL");
    }

    private void ensureColumnType(String tableName, String columnName, String targetDefinition) {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Integer.class, tableName);
        if (tableCount == null || tableCount == 0) {
            return;
        }
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        if (columnCount == null || columnCount == 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " " + targetDefinition);
    }
}
