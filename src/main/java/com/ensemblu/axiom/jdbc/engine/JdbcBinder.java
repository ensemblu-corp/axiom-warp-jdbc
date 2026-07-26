package com.ensemblu.axiom.jdbc.engine;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.foundation.Nothing;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.spec.database.binder.AxiomBinder;
import com.ensemblu.axiom.spec.database.contract.AxiomProtocol;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

public final class JdbcBinder implements AxiomBinder {
    private final PreparedStatement ps;

    public JdbcBinder(PreparedStatement ps) { this.ps = ps; }

    @Override
    public Result<Nothing> bindString(int i, String v) {//
        return Axiom.Check.attempt(() -> ps.setString(i, v));//
    }//

    @Override
    public Result<Nothing> bindInteger(int i, Integer v) {//
        return Axiom.Check.attempt(() -> ps.setInt(i, v)); //
    }//

    @Override
    public Result<Nothing> bindLong(int i, Long v) {//
        return Axiom.Check.attempt(() -> ps.setLong(i, v));//
    }//

    @Override
    public Result<Nothing> bindDouble(int i, Double v) {//
        return Axiom.Check.attempt(() -> ps.setDouble(i, v)); //
    }//

    @Override
    public Result<Nothing> bindBoolean(int i, Boolean v) {//
        return Axiom.Check.attempt(() -> ps.setBoolean(i, v));//
    }//

    @Override
    public Result<Nothing> bindTimestamp(int i, Date v) {//
        return Axiom.Check.attempt(() -> ps.setTimestamp(i, new Timestamp(v.getTime())));//
    }//

    @Override
    public Result<Nothing> bindNull(int index, AxiomProtocol protocol) {
        final var sqlType = switch (protocol) {
            case OPAQUE -> Types.OTHER;
            case STRING -> Types.VARCHAR;
            case INTEGER -> Types.INTEGER;
            case LONG -> Types.BIGINT;
            case DOUBLE -> Types.DOUBLE;
            case BOOLEAN -> Types.BOOLEAN;
            case TIMESTAMP -> Types.TIMESTAMP;
        };
        return Axiom.Check.attempt(() -> ps.setNull(index, sqlType));
    }
}