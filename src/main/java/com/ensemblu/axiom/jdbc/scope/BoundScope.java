package com.ensemblu.axiom.jdbc.scope;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.core.function.ThrowingSupplier;

import java.sql.Connection;
import java.util.function.Supplier;

public final class BoundScope {
    public static ScopedValue<Connection> getTxConn() {
        return TX_CONN;
    }

    static final ScopedValue<Connection> TX_CONN = ScopedValue.newInstance();

    private BoundScope() {
        throw new UnsupportedOperationException("🛡️BoundScope Guard: This class is a static scope manager. No instantiation allowed.");
    }

    /**
     * THE ROOM: Binds the connection and ensures it is closed.
     * It no longer makes transaction decisions.
     */
    public static <T> Result<T> use(Connection conn, ThrowingSupplier<T> op) {
        return Axiom.Check.attempt(() ->
                ScopedValue.where(TX_CONN, conn).call(() -> {
                    try {
                        return op.get();
                    } finally {
                        try {
                            if (!conn.isClosed()) conn.setAutoCommit(true);
                        } catch (Exception ignored) {
                        }
                        conn.close();
                    }
                })
        );
    }

    public static <T> Result<T> transaction(Supplier<Result<T>> businessLogic) {
        final var conn = current();
        try {
            conn.setAutoCommit(false);
            final var result = businessLogic.get();
            if (result.isSuccess()) {
                conn.commit();
            } else {
                conn.rollback();
            }
            return result;
        } catch (Exception e) {
            try { conn.rollback(); } catch (Exception ignored) {}
            return Axiom.Check.failure("Transaction Strike Failed: " + e.getMessage());
        } finally {
            try { conn.setAutoCommit(true); } catch (Exception ignored) {}
        }
    }

    /**
     * 🛡️ The Handshake: Ensures the thread is legally 'armed' with a connection.
     * If this fails, the worker likely bypassed a run() or runAtomic() gate.
     */
    public static Connection current() {
        if (!TX_CONN.isBound()) {
            throw new IllegalStateException(
                    """
                    Perimeter Breach: No database connection bound to this thread.
                    Ensure execution is wrapped in an AxiomWarp gateway (read/write).
                    See: api.com.ensemblu.axiom.jdbc.AxiomWarp
                   """
            );
        }
        return TX_CONN.get();
    }
}
