package com.vaadin.expensemanager.base;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The rows of some tables, taken before a test and put back after it, for UI tests
 * that commit what they write. A browser's request threads never see a test's
 * transaction, so {@code @Transactional} rollback only works on Browserless; this
 * gives every Testing Tool the same clean slate.
 *
 * <p>Restores by primary key {@code id}: rows the test added are deleted, rows it
 * changed get every column back. It never deletes a row that was there before, so
 * foreign keys from other tables keep pointing at something. Tables are restored in
 * the reverse of the order given, so pass parents before children.
 */
public final class TableSnapshot {

    private final JdbcTemplate jdbc;
    private final Map<String, List<Map<String, Object>>> rows;

    private TableSnapshot(JdbcTemplate jdbc, Map<String, List<Map<String, Object>>> rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    /** Takes the current rows of {@code tables} (parents first). */
    public static TableSnapshot take(JdbcTemplate jdbc, String... tables) {
        var rows = new java.util.LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : tables) {
            rows.put(table, jdbc.queryForList("select * from " + table));
        }
        return new TableSnapshot(jdbc, rows);
    }

    /** Puts every table back as it was when the snapshot was taken. */
    public void restore() {
        var tables = new java.util.ArrayList<>(rows.keySet());
        java.util.Collections.reverse(tables);
        for (String table : tables) {
            List<Map<String, Object>> before = rows.get(table);
            Set<Object> ids = before.stream().map(row -> row.get("id")).collect(Collectors.toSet());
            for (Map<String, Object> current : jdbc.queryForList("select id from " + table)) {
                if (!ids.contains(current.get("id"))) {
                    jdbc.update("delete from " + table + " where id = ?", current.get("id"));
                }
            }
            for (Map<String, Object> row : before) {
                var columns = row.keySet().stream().filter(c -> !c.equals("id")).toList();
                String set = columns.stream().map(c -> c + " = ?").collect(Collectors.joining(", "));
                Object[] args = new Object[columns.size() + 1];
                for (int i = 0; i < columns.size(); i++) {
                    args[i] = row.get(columns.get(i));
                }
                args[columns.size()] = row.get("id");
                jdbc.update("update " + table + " set " + set + " where id = ?", args);
            }
        }
    }
}
