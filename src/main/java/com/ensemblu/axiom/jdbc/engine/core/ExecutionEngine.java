package com.ensemblu.axiom.jdbc.engine.core;

import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.spec.database.contract.StrikeInstruction;
import com.ensemblu.axiom.spec.parser.SqlParser;

public interface ExecutionEngine<T> {
    Result<T> bindAndExecute(SqlParser.ExecutionPlan plan, StrikeInstruction instruction);
}