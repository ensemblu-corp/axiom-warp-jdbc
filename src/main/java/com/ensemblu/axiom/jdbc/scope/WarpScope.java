package com.ensemblu.axiom.jdbc.scope;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.jdbc.engine.JdbcExecutionEngine;
import com.ensemblu.axiom.spec.database.contract.StrikeInstruction;
import com.ensemblu.axiom.jdbc.provision.RawProvisioner;
import com.ensemblu.axiom.jdbc.engine.core.SovereignGate;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

public interface WarpScope {

    static Result<PersistentList<PersistentMap<String, Object>>> //
    joinResults(//
            RawProvisioner factory,//
            List<StrikeInstruction> tasks) {//
        if (tasks == null || tasks.isEmpty()) //
            return Axiom.Check.failure("No tasks to execute");

        try (final var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll())) {
            var subtasks = tasks.stream().map(instr -> scope.fork(() -> {
                try (final var conn = factory.getSovereignDataSource().getConnection()) {
                    final var engine = new JdbcExecutionEngine(conn);
                    return SovereignGate.execute(instr, engine);
                } catch (Exception e) {
                    return Axiom//
                            .Check//
                            .<PersistentList<PersistentMap<String, Object>>>//
                            failure("Establish a connection with the data source FAILED!",e);
                }
            })).toList();

            scope.join();

            var aggregatedResults = Axiom.Data.<PersistentMap<String, Object>>emptyList().asTransient();

            for (final var st : subtasks) {
                if (st.state() != StructuredTaskScope.Subtask.State.SUCCESS) {
                    return Axiom.Check.failure("Scope breach: A task failed to complete.");
                }

                final var res = st.get();
                if (res.isFailure()) return Axiom.Check.failure("Strike failed: " + res.failureValue().getMessage());

                final var resultList = res.getOrThrow();

                aggregatedResults = resultList.fold(aggregatedResults, (acc, row) -> acc.append(row));
            }

            return Axiom.Check.success(aggregatedResults.freeze());
        } catch (Exception e) {
            return Axiom.Check.failure("Parallel execution scope breached: " + e.getMessage());
        }
    }
}