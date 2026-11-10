package com.vaadin.expensemanager.base;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Restores the database to its post-boot state after every test — the data
 * isolation for UI tests that run in a real browser (ADR-0027, proposed).
 *
 * <p>{@code @Transactional} rollback cannot isolate those: on Playwright the view
 * runs on a server request thread outside the test's transaction, so it neither
 * sees the test's uncommitted seed data nor has its own commits rolled back. This
 * extension gives every Testing Tool the same semantics instead: everything commits,
 * and the database is put back afterwards.
 *
 * <p>The first time it runs in a JVM — after the Spring context has booted, so
 * Flyway's seeds and the {@code test}-profile {@code LocalUserSeeder} have both run —
 * it copies every table of the {@code public} schema into a {@code test_snapshot}
 * schema. After each test it truncates those tables and copies the rows back, with
 * {@code session_replication_role = replica} so foreign keys do not dictate the
 * order. Identity sequences are deliberately <em>not</em> reset: ids keep climbing,
 * which can never collide with a restored row.
 *
 * <p>Taken once per JVM, not per context: every Spring context in the run points
 * at the same singleton container, so one snapshot serves them all.
 */
public class DatabaseSnapshotExtension implements BeforeEachCallback, AfterEachCallback {

    private static final String SNAPSHOT_SCHEMA = "test_snapshot";
    private static final String EXCLUDED = "flyway_schema_history";

    private static List<String> tables;

    @Override
    public void beforeEach(ExtensionContext context) throws SQLException {
        synchronized (DatabaseSnapshotExtension.class) {
            if (tables == null) {
                tables = snapshot(dataSource(context));
            }
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws SQLException {
        restore(dataSource(context));
    }

    private static DataSource dataSource(ExtensionContext context) {
        return SpringExtension.getApplicationContext(context).getBean(DataSource.class);
    }

    private static List<String> snapshot(DataSource dataSource) throws SQLException {
        var names = new ArrayList<String>();
        try (Connection c = dataSource.getConnection(); var s = c.createStatement()) {
            try (var rs = s.executeQuery("""
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                    ORDER BY table_name""")) {
                while (rs.next()) {
                    if (!EXCLUDED.equals(rs.getString(1))) {
                        names.add(rs.getString(1));
                    }
                }
            }
            s.execute("DROP SCHEMA IF EXISTS " + SNAPSHOT_SCHEMA + " CASCADE");
            s.execute("CREATE SCHEMA " + SNAPSHOT_SCHEMA);
            for (var table : names) {
                s.execute("CREATE TABLE %s.%s AS TABLE public.%s"
                        .formatted(SNAPSHOT_SCHEMA, quote(table), quote(table)));
            }
        }
        return List.copyOf(names);
    }

    private static void restore(DataSource dataSource) throws SQLException {
        if (tables == null || tables.isEmpty()) {
            return;
        }
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try (var s = c.createStatement()) {
                s.execute("SET LOCAL session_replication_role = replica");
                s.execute("TRUNCATE " + String.join(", ",
                        tables.stream().map(t -> "public." + quote(t)).toList()));
                for (var table : tables) {
                    s.execute("INSERT INTO public.%s OVERRIDING SYSTEM VALUE SELECT * FROM %s.%s"
                            .formatted(quote(table), SNAPSHOT_SCHEMA, quote(table)));
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        }
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
