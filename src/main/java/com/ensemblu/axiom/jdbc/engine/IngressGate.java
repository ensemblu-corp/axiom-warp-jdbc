package com.ensemblu.axiom.jdbc.engine;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.foundation.Dop;
import com.ensemblu.axiom.jdbc.scope.BoundScope;

import com.ensemblu.axiom.spec.database.binder.IngressBinder;
import com.ensemblu.axiom.spec.database.contract.AxiomProtocol;
import com.ensemblu.axiom.spec.database.contract.StrikeInstruction;
import com.ensemblu.axiom.jdbc.engine.core.SovereignGate;
import com.ensemblu.axiom.spec.parser.SqlParser;

import static com.ensemblu.axiom.spec.database.integrity.IngressIntegrity.verifyAlignment;


public interface IngressGate {

    static TypedListStrike batch(String sqlTemplate) {
        return types -> dataList -> batch(sqlTemplate, types, dataList);
    }

    static Result<PersistentList<Integer>> batch(String sqlTemplate,//
                                                 PersistentMap<String, AxiomProtocol> types,//
                                                 PersistentList<PersistentMap<String, Object>> dataList)//
    {
        return Axiom.Check.attempt(() -> {
            final var plan = SqlParser.forge(sqlTemplate);

            dataList.forEach(data ->
                verifyAlignment(plan, types, data)
            );

            final var conn = BoundScope.current();

            try (final var pstmt = conn.prepareStatement(plan.sql())) {

                for (int i = 0; i < dataList.size(); i++) {
                    final var data = dataList.get(i);

                    final var binder = new JdbcBinder(pstmt);
                    IngressBinder.apply(binder, plan, types, data);
                    pstmt.addBatch();
                }

                final var rawResults = pstmt.executeBatch();
                // Use Dop correctly: pour an array, then deploy
                var projector = Dop.<Integer>projectList();
                for (int res : rawResults) {
                    projector = projector.append(res);
                }
                return projector.deploy();
            }
        }).prependFailureMessage("Axiom DB Batch Error: ");
    }


    static Result<PersistentList<PersistentMap<String, Object>>> //
    strike(StrikeInstruction instr) {//

        return (BoundScope.getTxConn().isBound())
                ? performStrike(instr) //
                : Axiom.Check.failure("Perimeter Breach: strike() must be called within a transactional scope.");
    }

    static Result<PersistentList<PersistentMap<String, Object>>> //
    performStrike(StrikeInstruction instr) {//
        return SovereignGate.execute(instr, new JdbcExecutionEngine());
    }

    static Result<PersistentList<PersistentMap<String, Object>>> shot(String sql) {
        return Axiom.Check.attempt(() -> {
            final var conn = BoundScope.current();
            try (final var stmt = conn.createStatement()) {
                final var hasResultSet = stmt.execute(sql);

                if (hasResultSet) {
                    try (final var rs = stmt.getResultSet()) {
                        return JdbcResultConverter.convert(rs);
                    }
                } else {
                    // It was an UPDATE/INSERT/DELETE, return the update count
                    final var updateCount = stmt.getUpdateCount();
                    final var map =  Axiom.Data.<String, Object>emptyMap().put("count", updateCount);
                    return Axiom.Data.<PersistentMap<String, Object>>emptyList().append(map);
                }
            }
        });
    }

    @FunctionalInterface
    interface TypedListStrike {
        DataListBinder withContract(PersistentMap<String, AxiomProtocol> types);
    }

    @FunctionalInterface
    interface DataListBinder {
        Result<PersistentList<Integer>> withData(PersistentList<PersistentMap<String, Object>> data);
    }

    @FunctionalInterface
    interface TypedStrike {
        DataBinder withContract(PersistentMap<String, AxiomProtocol> types);
    }

    @FunctionalInterface
    interface DataBinder {
        Result<PersistentList<PersistentMap<String, Object>>> withData(PersistentMap<String, Object> data);
    }
}