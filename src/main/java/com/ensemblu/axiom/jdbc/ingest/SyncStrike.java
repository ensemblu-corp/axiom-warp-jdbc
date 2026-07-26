package com.ensemblu.axiom.jdbc.ingest;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import com.ensemblu.axiom.core.data_structure.map.MapDelta;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.foundation.Dop;
import com.ensemblu.axiom.core.foundation.Nothing;
import com.ensemblu.axiom.jdbc.engine.IngressGate;
import com.ensemblu.axiom.jdbc.provision.RawProvisioner;
import com.ensemblu.axiom.spec.database.contract.AxiomProtocol;
import com.ensemblu.axiom.spec.database.contract.StrikeInstruction;
import com.ensemblu.axiom.spec.parser.SqlParser;


public interface SyncStrike {

  static AddDeleteCondition  table(RawProvisioner factory, String tableName) {
        return deleteCond -> //
                updateCond -> //
                        delta ->//
                                factory.runAtomic(() -> {//

                                    // 1. UPDATE FIRST
                                    if (!delta.updated().isEmpty()) {
                                        prepareUpdate(tableName, interpolate(updateCond, delta.updated()), delta.updated())
                                                .getOrThrow(); // This forces a rollback if the update fails
                                    }

                                    // 2. DELETE SECOND
                                    if (!delta.removed().isEmpty()) {
                                        final String sql = String.format("DELETE FROM %s WHERE %s", tableName, interpolate(deleteCond, delta.removed()));
                                        IngressGate.shot(sql).getOrThrow(); // Forces rollback on failure
                                    }

                                    // 3. INSERT LAST
                                    if (!delta.added().isEmpty()) {
                                        prepareInsert(tableName, delta.added()).getOrThrow(); // Forces rollback on failure
                                    }

            return Axiom.Check.success(Nothing.INSTANCE);
        });
    }

    private static Result<PersistentList<PersistentMap<String, Object>>> prepareInsert(String table, PersistentMap<String, Object> added) {
        final var sql = generateInsertTemplate(table, added);

        return IngressGate.strike(StrikeInstruction.dynamic(sql)//
                        .withContract(opaqueContract(added))//
                       .withData(added));
    }

    private static Result<PersistentList<PersistentMap<String, Object>>>
    prepareUpdate( String table, String condition, PersistentMap<String, Object> updated) {
        final var sql = generateUpdateTemplate(table,condition, updated);

        return IngressGate.strike(StrikeInstruction.dynamic(sql)//
                        .withContract(opaqueContract(updated))//
                        .withData(updated));
    }

      private static PersistentMap<String, AxiomProtocol> opaqueContract(PersistentMap<String, Object> data) {
        return Dop.project(data)//
                .mapValues(v -> AxiomProtocol.OPAQUE)//
                .deploy();//
    }

    static String generateInsertTemplate(String table, PersistentMap<String, Object> row) {
        final var cols = new StringBuilder();
        final var vals = new StringBuilder();
        row.forEach((k, v) -> {
            if (!cols.isEmpty()) {
                cols.append(", ");
                vals.append(", ");
            }
            cols.append(k);

            vals.append(SqlParser.SIGNAL).append(k);
        });
        return "INSERT INTO %s (%s) VALUES (%s)".formatted(table, cols, vals);
    }

    private static String generateUpdateTemplate(String table, String condition, PersistentMap<String, Object> row) {
        final var sets = new StringBuilder();
        row.forEach((k, v) -> {
            if (!sets.isEmpty()) sets.append(", ");
            // Use SqlParser.SIGNAL here
            sets.append(k).append(" = ").append(SqlParser.SIGNAL).append(k);
        });
        return "UPDATE %s SET %s WHERE %s".formatted( table, sets, condition);
    }

    private static String interpolate(String sql, PersistentMap<String, Object> data) {

        final var pattern = java.util.regex.Pattern.compile(SqlParser.SIGNAL +"([a-zA-Z0-9_]+)");
        final var matcher = pattern.matcher(sql);

        final var sb = new StringBuilder();
        var lastEnd = 0;
        while (matcher.find()) {
            sb.append(sql, lastEnd, matcher.start());
            final var key = matcher.group(1); //

            final var value = data.get(key);

            if (value instanceof String) {
                sb.append("'").append(value).append("'");
            } else {
                sb.append(value); //
            }
            lastEnd = matcher.end();
        }
        sb.append(sql.substring(lastEnd));

        return sb.toString();
    }

    @FunctionalInterface interface AddDeleteCondition { AddUpdateCondition whereDelete(String condition); }
    @FunctionalInterface interface AddUpdateCondition { AddDelta whereUpdate(String condition); }
    @FunctionalInterface interface AddDelta { Result<Nothing> withDelta(MapDelta<String, Object> delta); }
}