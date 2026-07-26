# Axiom JDBC

A zero-reflection, zero-annotation JDBC execution engine for the **Axiom** framework — built on Java 26+ virtual threads, `ScopedValue`, and `StructuredTaskScope`, and designed around Axiom's Data-Oriented Programming (DOP) philosophy.

Axiom JDBC gives you a fully explicit, contract-driven data layer: no ORM magic, no annotations, no proxies, no reflection. Every query is either an explicit `StrikeInstruction` with a declared type contract, or a raw "shot" for ad-hoc SQL — and every result is an immutable `PersistentList<PersistentMap<String, Object>>`.

---

## 🏛️ Integration  
  
Summon the Specification engine into your project:  
  
**Maven**  
  
```xml 
<dependency>    
     <groupId>com.ensemblu</groupId>   
     <artifactId>axiom-warp-jdbc</artifactId>   
     <version>1.0.0</version>  
</dependency>   
```   
**Gradle**  
  
```groovy
 implementation("com.ensemblu:axiom-warp-jdbc:1.0.0")   
```


## Terminology

The codebase uses a consistent internal vocabulary:

| Term | Meaning |
|---|---|
| **Strike** | A single database operation (query/update) |
| **Shot** | A simple, un-contracted, ad-hoc SQL execution |
| **Batch** | A multi-row typed strike executed via JDBC batching |
| **Arm** | To execute/trigger an operation |
| **Breach** | An error/failure condition |
| **Warp** | The top-level entry gateway (`AxiomWarp`) into the JDBC engine |
| **Gate** | A boundary that enforces a rule before letting execution through (`IngressGate`, `SovereignGate`) |
| **Hammer** | The bulk CSV-to-database ingestion pipeline |
| **Perimeter Breach** | An attempt to run DB logic outside of a bound connection scope |

---

## Package Structure

```
com.ensemblu.axiom.jdbc
├── api
│   └── AxiomWarp.java              // Top-level entry point / facade
├── engine
│   ├── IngressGate.java            // Core strike/shot/batch execution surface
│   ├── JdbcBinder.java             // Binds typed values onto PreparedStatement
│   ├── JdbcExecutionEngine.java    // Executes a parsed plan against a Connection
│   ├── JdbcResultConverter.java    // ResultSet -> PersistentList<PersistentMap>
│   ├── JdbcResultRow.java          // Single-row navigator/materializer
│   └── core
│       ├── ExecutionEngine.java    // Execution engine contract
│       └── SovereignGate.java      // Plan parsing + integrity verification gate
├── ingest
│   ├── Hammer.java                 // Bulk CSV -> table ingestion
│   └── SyncStrike.java             // MapDelta -> DB mirroring (insert/update/delete)
├── io
│   └── CsvEngine.java              // Streaming CSV reader/cursor
├── provision
│   ├── RawProvisioner.java         // Connection pool / DataSource bootstrap
│   └── SovereignDataSource.java    // Closeable DataSource contract
└── scope
    ├── BoundScope.java             // ScopedValue-based connection + transaction binding
    └── WarpScope.java              // Structured concurrency parallel execution
```

---

## Requirements

- **Java 26+** (uses `ScopedValue` and `StructuredTaskScope`, both preview/incubating APIs in recent JDKs — check your JDK version flags)
- A JDBC-compatible `DataSource`
- Axiom core (`com.ensemblu.axiom.core.*`) and Axiom spec (`com.ensemblu.axiom.spec.*`) modules on the classpath

---

## Getting Started

### 1. Provision a connection pool

```java
RawProvisioner provisioner = AxiomWarp.connect(configSource)
        .withPoolProvider(config -> MyPoolImplementation.from(config))
        .validateRules()
        .getOrThrow();

AxiomWarp warp = new AxiomWarp(provisioner);
```

`ConfigSource` is validated against Axiom's `DefaultDataContract`, including pool-size sanity checks (`engine.pool.min` / `engine.pool.max`).

### 2. Read data

```java
Result<PersistentList<PersistentMap<String, Object>>> rows =
        warp.read(() -> warp.strike().shot("SELECT * FROM users").getOrThrow());
```

### 3. Write data (transactional)

```java
import com.ensemblu.axiom.api.Axiom;

Result<PersistentList<PersistentMap<String, Object>>> result =
        warp.write(() ->
                warp.strike()
                        .dynamic("INSERT INTO users (id, name) VALUES (:java.id, :java.name)")
                        .withContract(Axiom
                                        .Data
                                        .<String,AxiomProtocol>emptyMap()
                                        .put("id",AxiomProtocol.LONG)
                                        .put("name",AxiomProtocol.STRING))
                        .withData(Axiom
                                .Data
                                .<String,Object>emptyMap()
                                .put("id",1L)
                                .put("name","Ofek")));
```

All writes run inside `RawProvisioner.runAtomic`, which binds the connection via `BoundScope.use` and wraps the logic in `BoundScope.transaction` — commit on success, rollback on failure or exception.

### 4. Batch / bulk typed strikes

```java
warp.write(() ->
        warp.strike()
            .bulk("INSERT INTO users (id, name) VALUES (:java.id, :java.name)")
            .withContract(types)
            .withData(listOfRows)
);
```

### 5. Bulk-ingest a CSV file

```java
warp.ingest()
    .fromFile("users.csv")
    .usingFileHeaders()
    .onTableName("users")
    .ingest();   // Result<Long> — rows ingested
```

`Hammer` streams the CSV row-by-row via `CsvEngine`, derives the insert SQL from the first row's keys, and batches inserts in chunks of `BATCH_SIZE` (1,000 rows) inside a single prepared statement.

### 6. Sync a delta to the database

```java
warp.sync()
    .tableName("users")
    .whereDelete("id = :java.id")
    .whereUpdate("id = :java.id")
    .withDelta(mapDelta);   // Result<Nothing>
```

`SyncStrike` mirrors a `MapDelta` (added/updated/removed) to a table in a single atomic transaction: **update → delete → insert**, in that order, each step forcing a rollback on failure via `getOrThrow()`.

### 7. Run strikes in parallel

```java
Result<PersistentList<PersistentMap<String, Object>>> results =
        warp.parallel(List.of(instructionA, instructionB, instructionC));
```

`WarpScope` opens a `StructuredTaskScope`, forks one JDBC connection + execution per instruction, joins them all, and aggregates results into a single flattened `PersistentList` — or fails fast if any subtask fails.

### 8. Shutdown

```java
warp.shutdown();
```

---

## Core Concepts

### Strikes and the Sovereign Gate

Every typed strike passes through `SovereignGate.execute`, which:

1. Parses the SQL into an `ExecutionPlan` (`SqlParser.forge`)
2. Verifies the declared type contract aligns with the plan and the supplied data (`IngressIntegrity.verifyAlignment`)
3. Delegates actual binding + execution to an `ExecutionEngine` (normally `JdbcExecutionEngine`)

This "verify before execute" gate is what prevents mismatched contracts/data from ever reaching the JDBC driver.

### Connection Scoping

`BoundScope` uses a `ScopedValue<Connection>` to bind the "current" connection for the duration of a logical unit of work. Any code that calls `BoundScope.current()` outside of an active scope throws an `IllegalStateException` — a **Perimeter Breach** — ensuring all database access is funneled through `AxiomWarp`'s `read`/`write` gateways.

### Result Materialization

`JdbcResultConverter` converts a raw `ResultSet` into a `PersistentList<PersistentMap<String, Object>>`, one immutable map per row, built via transient builders for efficient construction and then frozen.

`JdbcResultRow` provides a `ResultRow`-based single-row navigator with type-casting via `DataCast`, for column-by-column typed access.

### CSV Streaming

`CsvEngine` exposes a lazy, stateful `CsvStream` cursor over a classpath resource — headers can be read from the first line or supplied manually, blank lines are skipped automatically, and the stream self-closes when exhausted or via `count()` / `head()` terminal operations.

---

## Design Notes

- **No JPA/Hibernate-style entity mapping.** Rows are plain `PersistentMap<String, Object>` — you read/write by key, always.
- **No connection leakage.** `BoundScope.use` guarantees `setAutoCommit(true)` and `close()` run in a `finally` block regardless of outcome.
- **Batches respect insertion order.** `Hammer`'s SQL is derived from the first CSV row's column order, and every subsequent row is bound against that same blueprint — guaranteeing `?` placeholder alignment.
- **Parallel execution isolates connections.** `WarpScope` acquires a dedicated `Connection` per forked task rather than sharing the scoped connection, since `ScopedValue` bindings don't cross task boundaries safely in this model.

> **Warning**: Axiom JDBC enforces a strict **Single-Map** mandate. If you are accustomed to framework-managed object mapping, this library will require a shift in architectural mindset—from mapping classes to mapping **Truth**.

_Project Axiom: Built for engineers who value explicit contracts, predictable behavior, and complete control over their data layer._

---
  
## 📜 Legal  
  
This project is governed by the principles of immutable software architecture. See `LICENSE.md` for the specific terms of use.