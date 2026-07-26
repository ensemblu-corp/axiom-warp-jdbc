package com.ensemblu.axiom.jdbc.api;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.core.config.ConfigSource;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import com.ensemblu.axiom.core.function.ThrowingSupplier;
import com.ensemblu.axiom.core.foundation.Nothing;
import com.ensemblu.axiom.spec.database.contract.StrikeInstruction;
import com.ensemblu.axiom.jdbc.engine.IngressGate;
import com.ensemblu.axiom.jdbc.ingest.Hammer;
import com.ensemblu.axiom.jdbc.ingest.SyncStrike;
import com.ensemblu.axiom.jdbc.io.CsvEngine;
import com.ensemblu.axiom.jdbc.provision.RawProvisioner;
import com.ensemblu.axiom.jdbc.scope.WarpScope;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * <h1>🏛️ Axiom JDBC Gateway</h1>
 * <p>
 * <b>⚖️ Architectural Mandate:</b><br>
 * Provides the Sovereign perimeter for JDBC operations. Bypasses Opaque-Object bureaucracy
 * by enforcing explicit {@code StrikeInstruction} contracts and immutable data flow.
 * </p>
 * <p>
 * <b>⚡ Operational Promise:</b><br>
 * Thread-confined transactional control via {@code BoundScope}. Every operation
 * is normalized through the IngressGate to prevent perimeter breaches.
 * </p>
 */
public final class AxiomWarp {

    private final RawProvisioner factory;
    private final Strike strikeFinger;
    private final Ingest ingestFinger;
    private final Sync syncFinger;

    public AxiomWarp(RawProvisioner factory) {
        Axiom.Check//
                .that(factory)//
                .is(Objects::nonNull, "RawProvisioner instance can't be null")//
                .will()//
                .thenApprovedOrElseThrowException();
        this.factory = factory;
        this.strikeFinger = new Strike();
        this.ingestFinger = new Ingest();
        this.syncFinger = new Sync();
    }

    private sealed interface OperationalFingers permits Strike, Ingest, Sync {}

    /** ⚔️ THE FIRST FINGER: High-Precision Extraction */
    public Strike strike() { return strikeFinger; }

    /** 📂 THE SECOND FINGER: Data Ingestion Pipelines */
    public Ingest ingest() { return ingestFinger; }

    /** 🌀 THE THIRD FINGER: Delta Mirroring */
    public Sync sync() { return syncFinger; }

    // --- STATIC ENTRY ---
    public static RawProvisioner.AddPoolProvider connect(ConfigSource config) {
        return RawProvisioner.basedOnConfigSource(config);
    }

    // --- THE GATEWAYS ---
    /** 🔍 Executes read-only logic within a scoped connection. */
    public <T> Result<T> read(ThrowingSupplier<T> logic) {
        return this.factory.run(logic);
    }

    /** ✍️ Executes atomic transactional logic. */
    public <T> Result<T> write(Supplier<Result<T>> logic) {
        return this.factory.runAtomic(logic);
    }

    /** 🚀 Executes strikes in parallel via StructuredTaskScope. */
    public Result<PersistentList<PersistentMap<String, Object>>> parallel(List<StrikeInstruction> tasks) {
        return WarpScope.joinResults(this.factory, tasks);
    }

    // --- LIFECYCLE ---
    public Nothing shutdown() {
        factory.shutdown();
        return Nothing.INSTANCE;
    }

    /** 🔍 High-Precision Execution (The Truth Engine) */
    public final class Strike implements OperationalFingers {
        public IngressGate.TypedStrike dynamic(String sql) {
            return types -> data ->
                    IngressGate.strike(StrikeInstruction.dynamic(sql).withContract(types).withData(data));
        }

        public Result<PersistentList<PersistentMap<String, Object>>> shot(String sql) {
            return IngressGate.shot(sql);
        }

        public IngressGate.TypedListStrike bulk(String sql) {
            return types -> data -> IngressGate.batch(sql, types, data);
        }
    }

    /** 📂 Bulk data ingestion pipelines */
    @SuppressWarnings("InnerClassMayBeStatic")
    public final class Ingest implements OperationalFingers {
        public Hammer.AddHeaders fromFile(String path) {
            return Hammer.fromFile(path);
        }

        public CsvEngine.AddHeaders streamFromPath(String path) {
            return CsvEngine.streamFromPath(path);
        }
    }

    /** 🌀 Delta Mirroring: Atomic state synchronization */
    public final class Sync implements OperationalFingers {
        /** Mirror a MapDelta to the database in an atomic transaction. */
        public SyncStrike.AddDeleteCondition tableName(String tableName) {
            return SyncStrike.table(factory, tableName);
        }
    }
}