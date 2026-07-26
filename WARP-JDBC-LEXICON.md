# 🌌 THE WARP-JDBC LEXICON

> "We do not map objects to tables; we materialize state from the metal."

This document defines the high-density terminology of the **Axiom-Warp-JDBC** ecosystem. To speak the language is to understand the mechanics. We have purged the "Corporate CRUD" dialect to ensure that every term reflects the physical reality of data movement.

---

## 🏗️ 1. RESOURCE CONTROL (The Provision)

In the Axiom world, we don't "manage beans." We **Provision** the metal.

| Term | Definition | Mechanical Reality |
| :--- | :--- | :--- |
| **Provision** | The act of supplying raw resources. | Direct access to `java.sql.Connection` via a `javax.sql.DataSource`, with no framework intervention. |
| **SovereignDataSource** | The origin of connectivity. | A `DataSource` + `AutoCloseable` contract. Serves the engine directly and guarantees a clean `shutdown()` — answers to no one but the caller. |
| **RawProvisioner** | The connection handler. | Validates pool config against the data contract (`engine.pool.min`/`max`), then acquires and hands off a clean, unproxied `Connection` for the duration of a strike. |

---

## 🛡️ 2. EXECUTION BOUNDARIES (The Scope)

We don't use "Magic Annotations" to handle transactions. We use **Explicit Scopes**.

| Term | Definition | Mechanical Reality |
| :--- | :--- | :--- |
| **BoundScope** | A physical lock on a connection. | A `ScopedValue<Connection>` clamps one `Connection` to the current thread of execution for the life of the call. It cannot leak; it cannot be silently shared. Calling for a connection outside a bound scope throws a **Perimeter Breach**. |
| **Warp** | The gateway leap. | `AxiomWarp` — the single facade through which all reads, writes, ingestion, sync, and parallel strikes enter the engine. There is no back door. |
| **WarpScope** | The concurrency fork. | Not reactive streams — **structured concurrency**. `StructuredTaskScope` forks one connection + strike per task, joins them all, and fails the whole scope if any subtask fails. |
| **Perimeter Breach** | An illegal execution state. | Thrown when code attempts to touch the database outside of a `BoundScope`-managed thread — proof that every access path routes through `AxiomWarp`. |

---

## 🔨 3. DATA MOVEMENT (The Ingress)

Data does not "save"—it **Strikes**, or it is **Hammered**.

| Term | Definition | Mechanical Reality |
| :--- | :--- | :--- |
| **Ingress Gate** | The singular entry point. | Replaces the "Repository." `IngressGate` exposes `strike`, `shot`, and `batch` — every write or read funnels through here, contract-checked before it touches JDBC. |
| **The Hammer** | High-pressure ingestion. | `Hammer` — a bulk CSV-to-table loader. Derives the insert SQL from the first row's column blueprint, then batches the rest in chunks of 1,000 rows for maximum throughput. |
| **The Forge** | The plan-chamber. | `SqlParser.forge` — turns a raw SQL string into a structured `ExecutionPlan` before any binding or verification happens. Nothing executes unforged. |
| **Materializer** | The state-former. | Replaces "ORM." `JdbcResultConverter` and `JdbcResultRow` carve raw `ResultSet` rows into immutable **Persistent Maps (HAMT)** — no entities, no proxies, no lazy loading. |
| **Sync Strike** | Delta mirroring. | `SyncStrike` — takes a `MapDelta` (added/updated/removed) and mirrors it to a table in one atomic transaction: update → delete → insert, rollback on any failure. |

---

## 🧪 4. VALIDATION (The Strike)

We do not "test" our database logic in the abstract. We enforce **Structural Integrity** at the gate, on every single call.

| Term | Definition | Mechanical Reality |
| :--- | :--- | :--- |
| **Sovereign Gate** | The pre-execution checkpoint. | `SovereignGate.execute` — forges the SQL plan, then hands it to Integrity Verification *before* any `ExecutionEngine` is allowed to bind or run it. |
| **Integrity Verification** | The alignment check. | `IngressIntegrity.verifyAlignment` — proves that the declared type contract (`AxiomProtocol` map), the parsed SQL plan, and the actual data map agree, column for column. A mismatch never reaches the driver. |
| **Contract** | The declared law of a strike. | A `PersistentMap<String, AxiomProtocol>` stating exactly what type each bound value must be. There is no inference — only declaration. |

---

## ⚔️ THE COMMANDMENTS OF WARP

1.  **No Magic:** If you cannot see the connection being passed, it is a lie.
2.  **No Reflection:** We materialize data through explicit functions, not by hallucinating fields.
3.  **No Hibernate:** We speak SQL. The database is a partner, not a problem to be hidden.
4.  **No Silent Threads:** Every connection is `ScopedValue`-bound or `StructuredTaskScope`-forked — never ambient, never assumed.
5.  **Heavy Fabric:** Every implementation must be `final`. The architecture does not "stretch"; it is **330GSM Rigid**.
