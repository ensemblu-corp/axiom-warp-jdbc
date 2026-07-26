package com.ensemblu.axiom.jdbc.provision;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.core.config.ConfigSource;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.function.ThrowingSupplier;
import com.ensemblu.axiom.jdbc.scope.BoundScope;
import com.ensemblu.axiom.spec.database.materializer.DefaultDataContract;

import java.sql.Connection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Axiom Connection Forge: Zero-Dependency Blueprint.
 * We validate the rules, but the user provides the physical Source.
 */
public final class RawProvisioner {

    private final SovereignDataSource dataSource;

    private RawProvisioner(SovereignDataSource dataSource) {
        this.dataSource = dataSource;
    }


    public static AddPoolProvider basedOnConfigSource(ConfigSource config) {
        return poolProvider ->
                validatedRules ->//
                        Axiom.Check//
                                .that(validatedRules)//
                                .isNull()//
                                .will()//
                                .mapTo(_ ->
                                        DefaultDataContract.validate(config)
                                                .map(data-> {//
                                                    final var poolMax = (int) data.get("engine.pool.max");//
                                                    final var poolMin = //
                                                            config//
                                                            .targetField("engine.pool.min")//
                                                            .toIntResult()//
                                                            .validate(min -> min > 0, //
                                                                    "engine.pool.min must be positive number")//
                                                            .validate(min -> min > 1, //
                                                                    "engine.pool.min must be at least 2")//
                                                            .validate(min -> min <= poolMax, //
                                                                    "Structural Imbalance: engine.pool.min > engine.pool.max (" + poolMax + ")")//
                                                            .getOrElse(2);//

                                                     return  data.put("engine.pool.min",poolMin );
                                                })
                                )
                                .orGet(() -> Axiom.Check.attempt(() -> validatedRules.apply(config))
                                        .validate(Objects::nonNull, //
                                                "validatedRules provide null value"))//
                                .mapTry(poolProvider)//
                                .nameThrowingPredicate(Throwable::getMessage)//
                                .prependFailureMessage("Database Connection Pool Initialization Failed:")//
                                .map(RawProvisioner::new);
    }

    private Result<Connection> getConnection() {
        final ThrowingSupplier<Connection> connectionThrowingSupplier = dataSource::getConnection;

        return Axiom.Check//
                .attempt(connectionThrowingSupplier)//
                .prependFailureMessage("Failed to acquire connection: ");
    }

    /**
     * The "Scan" Gateway: No transaction overhead.
     * Entry Point 1: Standard Read
     */
    public <T> Result<T> run(ThrowingSupplier<T> op) {
        return this.getConnection().flatMap(conn ->
                BoundScope.use(conn, op)
        );
    }

    /**
     * The "Atomic" Gateway:
     * Refactored to handle the Result nesting cleanly.
     */
    public <T> Result<T> runAtomic(Supplier<Result<T>> businessLogic) {
        return this.getConnection()//
                .flatMap(conn ->//
                        BoundScope.use(conn, () -> BoundScope.transaction(businessLogic))//
                ).flatMap(result -> result); // Flatten the nested result safely
    }

    public SovereignDataSource getSovereignDataSource() {
        return this.dataSource;
    }

    public void shutdown() {
        dataSource.shutdown();
    }

    @FunctionalInterface
    public interface ProvisionSource {
        Result<PersistentMap<String, Object>> onSource(String catalogSource);
    }

    public interface AddPoolProvider {
        AddValidateRules withPoolProvider(Function<PersistentMap<String, Object>, SovereignDataSource> poolProvider);
    }

    public interface AddValidateRules {
        Result<RawProvisioner> validateRules(Function<ConfigSource, PersistentMap<String, Object>> validatedRules);

        default Result<RawProvisioner> validateRules() {
            return validateRules(null);
        }
    }
}