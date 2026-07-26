package com.ensemblu.axiom.jdbc.engine;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface JdbcResultConverter {
        static PersistentList<PersistentMap<String, Object>> convert(ResultSet rs) throws SQLException {
        final var meta = rs.getMetaData();
        final var columnCount = meta.getColumnCount();

        final var labels = new String[columnCount];
        for ( var i = 1; i <= columnCount; i++) labels[i-1] = meta.getColumnLabel(i);

        var builder = Axiom.Data.<PersistentMap<String, Object>>emptyList().asTransient();

        while (rs.next()) {
            var rowMap =  Axiom.Data.<String, Object>emptyMap().asTransient();

            for (int i = 0; i < columnCount; i++) {
                final var val = rs.getObject(i + 1);
                final var label = labels[i];
                rowMap = rowMap.put(label, val);
            }

            builder = builder.append(rowMap.freeze());
        }

        return builder.freeze();
    }
}