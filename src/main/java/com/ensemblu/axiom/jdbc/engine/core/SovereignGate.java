package com.ensemblu.axiom.jdbc.engine.core;

import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.spec.database.contract.StrikeInstruction;
import com.ensemblu.axiom.spec.database.integrity.IngressIntegrity;
import com.ensemblu.axiom.spec.parser.SqlParser;

public interface SovereignGate {
    static <T> Result<T> execute(
            StrikeInstruction instruction,
            ExecutionEngine<T> engine
    ) {
        final var plan = SqlParser.forge(instruction.sql());

        IngressIntegrity.verifyAlignment(plan, instruction.types(), instruction.data());

        return engine.bindAndExecute(plan, instruction);
    }
}