package com.ensemblu.axiom.jdbc.ingest;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.foundation.Dop;
import com.ensemblu.axiom.jdbc.io.CsvEngine;
import com.ensemblu.axiom.jdbc.scope.BoundScope;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;


public interface Hammer {

    int BATCH_SIZE = 1_000;

    static AddHeaders fromFile(final String path) {

        return headers -> tableName ->
                Axiom.Check.attempt(() -> {
                    final var csvStream = CsvEngine.streamFromPath(path).basedOnHeaders(headers).getOrThrow();

                    if (!csvStream.hasNextRow()) {
                        try { csvStream.close(); } catch (Exception ignored) {}
                        return 0L;
                    }

                    final var firstRow = csvStream.nextRow();
                    final var blueprint = Dop.project(firstRow).keys().deploy();

                    final var sql = forgeInsertSql(tableName, blueprint);
                    final var conn = BoundScope.current();

                    try (final var stmt = conn.prepareStatement(sql); csvStream) {
                        final var rowCounter = new AtomicLong(0);

                        bind(stmt, firstRow, blueprint);
                        stmt.addBatch();
                        rowCounter.incrementAndGet();

                        Dop.project(Axiom.Data.<PersistentMap<String, Object>>emptyList())//
                                .pour(csvStream)//
                                .whileTrue(CsvEngine.CsvStream::hasNextRow)//
                                .extract(streamCursor -> {//
                                    try {//
                                        final var normalizedRow = streamCursor.nextRow();

                                        bind(stmt, normalizedRow, blueprint);
                                        stmt.addBatch();

                                        if (rowCounter.incrementAndGet() % BATCH_SIZE == 0) {
                                            stmt.executeBatch();
                                        }

                                        return normalizedRow;
                                    } catch (SQLException e) {
                                        throw new RuntimeException("JDBC Ingress Failure", e);
                                    }
                                });

                        if (rowCounter.get() % BATCH_SIZE != 0) {
                            stmt.executeBatch();
                        }

                        return rowCounter.get();
                    }
                }).prependFailureMessage("Bulk Ingress Breach [" + tableName + "]: ");
    }

    private static String forgeInsertSql(String tableName, PersistentList<String> blueprint) {
        final var columns = new StringBuilder();
        final var markers = new StringBuilder();

        blueprint.forEach(key -> {
            if (!columns.isEmpty()) {
                columns.append(", ");
                markers.append(", ");
            }
            columns.append(key);
            markers.append("?");
        });

        return "INSERT INTO \"%s\" (%s) VALUES (%s)".formatted(tableName, columns, markers);
    }

    private static void bind(PreparedStatement stmt, PersistentMap<String, Object> row, PersistentList<String> blueprint) {
        final int[] index = {1};
        blueprint.forEach(key -> {
            try {
                stmt.setObject(index[0]++, Dop.normalize(row.get(key)));
            } catch (Exception e) {
                throw new RuntimeException("JDBC Binding Breach at key: " + key, e);
            }
        });
    }

    interface AddHeaders {
        default AddTableName basedOnHeaders(String... headers) {
            return basedOnHeaders(Axiom.Data.list(headers));
        }

        AddTableName basedOnHeaders(PersistentList<String> headers);

        default AddTableName usingFileHeaders() {
            return basedOnHeaders((PersistentList<String>) null);
        }
    }

    interface AddTableName {
        Result<Long> onTableName(String tableName);
    }

}