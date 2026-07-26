package com.ensemblu.axiom.jdbc.provision;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.foundation.Nothing;
import com.ensemblu.axiom.core.validation.Result;
import javax.sql.DataSource;

public interface SovereignDataSource extends DataSource, AutoCloseable {

    @Override
    void close() throws Exception;

    default Result<Nothing> shutdown() {
        return Axiom.Check.attempt(this::close);
    }
}