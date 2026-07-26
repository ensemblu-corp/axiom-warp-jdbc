package com.ensemblu.axiom.jdbc.engine;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.jdbc.engine.core.ExecutionEngine;
import com.ensemblu.axiom.jdbc.scope.BoundScope;
import com.ensemblu.axiom.spec.database.binder.AxiomBinder;
import com.ensemblu.axiom.spec.database.binder.IngressBinder;
import com.ensemblu.axiom.spec.database.contract.StrikeInstruction;
import com.ensemblu.axiom.spec.parser.SqlParser;

import java.sql.Connection;


public final class JdbcExecutionEngine implements ExecutionEngine<PersistentList<PersistentMap<String, Object>>> {

    private final Connection injectedConn;

    public JdbcExecutionEngine() {
        this.injectedConn = null;
    }

    public JdbcExecutionEngine(Connection conn) {
        this.injectedConn = conn;
    }

    @Override
    public Result<PersistentList<PersistentMap<String, Object>>> bindAndExecute(SqlParser.ExecutionPlan plan, StrikeInstruction instr) {
        return Axiom.Check.attempt(() -> {
            final var conn = (injectedConn != null) ? injectedConn : BoundScope.current();
            try (var pstmt = conn.prepareStatement(plan.sql())) {
                pstmt.clearParameters();
                final var binder = new JdbcBinder(pstmt);

                 IngressBinder.apply(binder, plan, instr.types(), instr.data());

                if (instr.sql().trim().toUpperCase().startsWith("SELECT")) {
                    try (final var rs = pstmt.executeQuery()) { return JdbcResultConverter.convert(rs); }
                } else {
                    final var updateCount = pstmt.executeUpdate();
                    return Axiom.Data.<PersistentMap<String, Object>>emptyList()
                            .append( Axiom.Data.<String, Object>emptyMap().put("count", updateCount));
                }
            }
        });
    }
}
