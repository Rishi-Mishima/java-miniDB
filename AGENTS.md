# AGENTS.md

Persistent context for future Codex agents working in this repository.

## Project Purpose

Java MiniDB is an educational Java database implementation inspired by MySQL-like database architecture. The goal is to expose database internals directly in code rather than hide them behind frameworks, ORMs, embedded databases, or third-party storage engines.

Treat this as a learning-oriented systems project. It is not a production database and should not be reshaped into one by replacing the core architecture.

## Architecture Overview

The project follows a layered mini database design:

```text
Client/Shell
  -> Transport
  -> Server/Executor
  -> Parser
  -> Table Manager (TBM)
  -> Version Manager (VM)
  -> Data Manager (DM)
  -> Transaction Manager (TM), Index Manager (IM), PageCache, Logger
```

Core concepts represented in the implementation:

- transaction status tracking through `.xid` files
- page-based storage and data-item encoding
- MVCC entries using `xmin` and `xmax`
- visibility rules for Read Committed and Repeatable Read
- UID-level locking and deadlock detection
- B+ tree indexing
- simple SQL-like parsing
- custom socket client/server communication

## Important Packages

- `top.risha.minidb.backend.tm`: transaction manager and XID status persistence.
- `top.risha.minidb.backend.dm`: data manager, data items, pages, page cache, logging, and recovery.
- `top.risha.minidb.backend.vm`: MVCC entries, visibility rules, active transaction state, and lock table.
- `top.risha.minidb.backend.im`: B+ tree index implementation.
- `top.risha.minidb.backend.tbm`: table metadata, field metadata, and table-level operations.
- `top.risha.minidb.backend.parse`: tokenizer, parser, and statement model classes.
- `top.risha.minidb.backend.server`: TCP server and request executor.
- `top.risha.minidb.client`: CLI client shell.
- `top.risha.minidb.transport`: package encoding and hex-based socket transport.
- `top.risha.minidb.common`: shared error definitions.
- `top.risha.minidb.backend.utils`: byte parsing and low-level helpers.

## Build Commands

Use Maven with Java 17.

```bash
mvn package
```

Run the backend:

```bash
mvn -q exec:java -Dexec.mainClass=top.risha.minidb.backend.Launcher -Dexec.args="-create /tmp/minidb"
mvn -q exec:java -Dexec.mainClass=top.risha.minidb.backend.Launcher -Dexec.args="-open /tmp/minidb -mem 64MB"
```

Run the client in another terminal:

```bash
mvn -q exec:java -Dexec.mainClass=top.risha.minidb.client.Launcher
```

The server listens on `127.0.0.1:9999`.

## Test Commands

There are currently no automated tests under `src/test/java`, but JUnit 4 is configured in `pom.xml`.

Always run at least:

```bash
mvn test
```

For changes that affect runtime behavior, also run:

```bash
mvn package
```

When touching server/client or storage paths, perform a smoke test with create/open plus simple `create table`, `insert`, `select`, `update`, and `delete` statements.

## Coding Conventions

Conventions inferred from the repository:

- Java 17, Maven, package root `top.risha.minidb`.
- Keep modules separated by database layer: TM, DM, VM, IM, TBM, parser, transport, client/server.
- Use explicit binary layouts and parser utilities rather than ad hoc byte manipulation when helpers exist.
- Follow the existing simple class/interface style and avoid introducing large frameworks.
- Keep comments useful for database internals and non-obvious byte-level formats.
- Prefer small, direct changes that preserve current module boundaries.
- Use existing `Error` constants and `Panic.panic(...)` style for fatal storage errors unless a broader error-handling change is requested.

## Rules For Making Changes

- Preserve the existing database architecture.
- Do not replace TM, DM, VM, IM, or TBM with external libraries, ORMs, embedded databases, or framework abstractions.
- Do not hide database logic behind third-party abstractions.
- Prefer minimal, focused fixes over broad rewrites.
- New features should build on the existing TM / DM / VM / IM / TBM architecture whenever possible.
- Keep public behavior aligned with the README and update docs when behavior changes.
- Do not claim production readiness or full SQL/MySQL/JDBC compatibility unless those features are actually implemented and tested.
- Be careful with existing uncommitted user changes; do not revert unrelated work.

## Known Fragile Areas

- Storage and recovery code is sensitive to byte offsets, page free-space offsets, and UID encoding.
- `Logger`, `Recover`, `PageCache`, `PageX`, and `DataItemImpl.after(...)` form a tightly coupled write/recovery path.
- MVCC correctness depends on transaction status persistence in TM and visibility checks in `Visibility`.
- `LockTable` deadlock detection and lock release behavior affect update/delete correctness.
- B+ tree node splitting, sibling traversal, and boot-root updates are easy to break with off-by-one errors.
- Query execution expects indexed fields in `where` clauses; there is no general table-scan planner.
- The SQL parser is intentionally small and does not support semicolons or full SQL syntax.
- The custom socket protocol is not MySQL wire protocol and not JDBC.

## Verification Expectations

After any code change, run tests/build commands before reporting completion:

```bash
mvn test
mvn package
```

For changes touching database runtime behavior, also verify a minimal end-to-end workflow:

```sql
create table user id int64, name string, age int32 (index id age)
insert into user values 1 Alice 24
select * from user where id = 1
update user set age = 25 where id = 1
delete from user where id = 1
```

If a command cannot be run because of sandbox, networking, or local environment permissions, state that clearly and include the best available partial verification.
