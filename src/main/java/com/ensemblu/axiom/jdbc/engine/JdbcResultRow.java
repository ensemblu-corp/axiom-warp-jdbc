package com.ensemblu.axiom.jdbc.engine;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.api.TargetNavigator;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import com.ensemblu.axiom.core.foundation.DataCast;
import com.ensemblu.axiom.core.foundation.Dop;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.spec.database.materializer.ResultRow;

import java.sql.*;
import java.util.Objects;

public record JdbcResultRow(ResultSet rs) implements ResultRow {

    private Object getValue(String column) {
        try { return rs.getObject(column); }
        catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public TargetNavigator navigate(String column) {
        return new TargetNavigator() {
            @Override
            public <T> Result<T> execute(DataCast.Protocol protocol) {
               final var val = getValue(column);
               return Objects.isNull(val) //
                       ? Axiom.Check.failure("Column not found: " + column)//
                       : DataCast.cast(Dop.normalize(val), protocol);
            }
        };
    }


    @Override
    public PersistentList<String> columns() {
        try {
            final var meta = rs.getMetaData();
            final var count = meta.getColumnCount();

            return Dop.<String>projectList()//
                    .pour(1)//
                    .whileTrue(i -> i <= count)//
                    .extract(this::safeGetLabel)//
                    .deploy();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get metadata", e);
        }
    }

    private String safeGetLabel(int index) {
        try {//
            return rs.getMetaData().getColumnLabel(index);//
        } catch (SQLException e) {//
            throw new RuntimeException("Error extracting column label at " + index, e);
        }//
    }
}