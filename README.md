
# 🔨 Axiom Warp JDBC

![Version](https://img.shields.io/badge/version-2.0.0-blue)
![Java](https://img.shields.io/badge/Java-26-orange)
![Depends](https://img.shields.io/badge/depends%20on-axiom--spec-informational)
![License](https://img.shields.io/badge/license-Limited%20Commercial-red)

**Zero-reflection, zero-annotation JDBC execution engine for Axiom.**

Built on Java 26 virtual threads, `ScopedValue`, and `StructuredTaskScope`. Every query is an explicit **Strike** with a declared type contract (or a raw **Shot** for ad-hoc SQL). Every result is an immutable `PersistentList<PersistentMap<String, Object>>`.

No ORM. No proxies. No annotations. No magic.

---

## Requirements

- **Java 26**
- [`axiom-spec`](https://github.com/ensemblu-corp/axiom-spec) `2.0.0` (and therefore `axiom`)
- A JDBC driver of your choice

---

## Installation

**Maven**

```xml
<dependency>
    <groupId>com.ensemblu</groupId>
    <artifactId>axiom-warp-jdbc</artifactId>
    <version>2.0.0</version>
</dependency>
```

**Gradle**

```groovy
implementation("com.ensemblu:axiom-warp-jdbc:2.0.0")
```

---

## Lexicon

| Term | Meaning |
|------|---------|
| **Strike** | A single database operation (query / update) with a type contract |
| **Shot** | Ad-hoc SQL without a formal contract |
| **Batch** | Multi-row typed strike via JDBC batching |
| **Arm** | Execute / trigger an operation |
| **Breach** | Error / failure condition |
| **Warp** | Top-level entry gateway (`AxiomWarp`) |
| **Gate** | Boundary that enforces rules before execution (`IngressGate`, `SovereignGate`) |
| **Hammer** | Bulk CSV → table ingestion pipeline |
| **Perimeter Breach** | Attempt to run DB logic outside a bound connection scope |

---

## Quick start

```java
import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.jdbc.api.AxiomWarp;
import com.ensemblu.axiom.spec.database.materializer.AxiomProtocol;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import com.ensemblu.axiom.core.validation.Result;

// warp is obtained from your provisioner / config (see RawProvisioner / SovereignDataSource)

// 1. Read (ad-hoc shot)
Result<PersistentList<PersistentMap<String, Object>>> rows =
        warp.read(() -> warp.strike().shot("SELECT * FROM users").getOrThrow());

// 2. Write (typed dynamic strike, transactional)
Result<PersistentList<PersistentMap<String, Object>>> result =
        warp.write(() ->
                warp.strike()
                        .dynamic("INSERT INTO users (id, name) VALUES (:java.id, :java.name)")
                        .withContract(Axiom.Data.<String, AxiomProtocol>emptyMap()
                                .put("id", AxiomProtocol.LONG)
                                .put("name", AxiomProtocol.STRING))
                        .withData(Axiom.Data.<String, Object>emptyMap()
                                .put("id", 1L)
                                .put("name", "Ofek")));

// 3. Batch / bulk typed strikes
warp.write(() ->
        warp.strike()
                .bulk("INSERT INTO users (id, name) VALUES (:java.id, :java.name)")
                .withContract(types)
                .withData(listOfRows));

// 4. Bulk-ingest a CSV file
warp.ingest()
        .fromFile("users.csv")
        .usingFileHeaders()
        .onTableName("users")
        .ingest();   // Result<Long> — rows ingested

// 5. Sync a MapDelta to the database
warp.sync()
        .tableName("users")
        .whereDelete("id = :java.id")
        .whereUpdate("id = :java.id")
        .withDelta(mapDelta);   // Result<Nothing>
```

---

## Package structure

```
com.ensemblu.axiom.jdbc
├── api
│   └── AxiomWarp.java              // Facade
├── engine
│   ├── IngressGate.java            // Strike / shot / batch surface
│   ├── JdbcBinder.java
│   ├── JdbcExecutionEngine.java
│   ├── JdbcResultConverter.java    // ResultSet → PersistentList<PersistentMap>
│   ├── JdbcResultRow.java
│   └── core
│       ├── ExecutionEngine.java
│       └── SovereignGate.java      // Plan + integrity verification
├── ingest
│   ├── Hammer.java                 // CSV → table
│   └── SyncStrike.java             // MapDelta → DB mirror
├── io
│   └── CsvEngine.java              // Byte-oriented CSV stream (2.0.0)
├── provision
│   ├── RawProvisioner.java
│   └── SovereignDataSource.java
└── scope
    ├── BoundScope.java             // ScopedValue<Connection>
    └── WarpScope.java              // Parallel execution + clearer errors
```

---

## Architecture highlights

### Verify before execute

`SovereignGate` parses the plan, checks contract / data alignment, then hands off to `JdbcExecutionEngine`. Mismatched contracts never reach the driver.

### Connection scoping

`BoundScope` binds the current `Connection` via `ScopedValue`. Calling `BoundScope.current()` outside an active scope throws — a **Perimeter Breach**. All access is forced through `AxiomWarp.read` / `write`.

### Result materialization

`JdbcResultConverter` turns a `ResultSet` into `PersistentList<PersistentMap<String, Object>>` using transient builders, then freezes them.

### CSV streaming (2.0.0)

`CsvEngine` loads the resource as a full `byte[]`, walks it with a cursor, and parses headers lazily. Blank lines are skipped. Compatible with the byte-based `CsvRowParser` in `axiom-spec`.

### Parallel execution

`WarpScope` acquires a **dedicated connection per forked task** (ScopedValue bindings do not cross task boundaries safely). Failure messages distinguish connection establishment from strike execution; interrupt status is restored correctly.

---

## Design principles

- **No entity mapping** — rows are plain `PersistentMap`s; you read/write by key.
- **No connection leakage** — `BoundScope.use` always restores auto-commit and closes in `finally`.
- **Batches preserve column order** — Hammer derives SQL from the first CSV row’s headers and binds every subsequent row to that blueprint.
- **Single-map mandate** — if you expect framework-managed object graphs, this library will feel strict by design.

> We do not map objects to tables; we materialize state from the metal.

---

## Related modules

| Module | Relationship |
|--------|----------------|
| `axiom-spec` | Parsers, `SqlParser`, materializers, protocols |
| `axiom` | `PersistentMap` / `PersistentList` / `Result` |
| `axiom-warp-reactive` | Non-blocking counterpart on Vert.x |

---

## Legal

Limited Commercial License — free for evaluation, testing, and non-commercial development.  
Commercial or production use requires a paid annual contract from Ensemblu Corp.

See `LICENSE.md`. Contact: **contact@ensemblu.com**
