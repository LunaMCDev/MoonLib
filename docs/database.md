# Database

*Since 1.2.0.* `Database` gives a plugin SQL storage with a connection pool, prepared statements,
transactions, migrations and asynchronous queries, **with no extra dependency**: Spigot, Paper
and their forks already ship the SQLite and MySQL JDBC drivers.

| Engine | How | Driver |
|---|---|---|
| SQLite | `Database.sqlite(plugin, "data.db")` | bundled with the server |
| MySQL | `Database.mysql(plugin, host, port, db, user, pass)` | bundled with the server |
| MariaDB | `Database.mariadb(...)` | MariaDB driver if present, else the MySQL driver |
| PostgreSQL, H2, others | `Database.builder(plugin).url("jdbc:...")` | shade the driver yourself |
| Your own pool (HikariCP...) | `Database.fromDataSource(plugin, dataSource, dialect, threads)` | yours |

## Let server owners choose

```java
Database db = Database.fromConfig(this, loadConfig("config.yml"), "database");
```

```yaml
database:
  type: sqlite          # sqlite, mysql or mariadb
  file: data.db         # sqlite only, relative to the plugin folder
  host: localhost
  port: 3306
  name: prison
  username: root
  password: ''
  pool-size: 0          # 0 = automatic: 1 for SQLite (one writer at a time), 8 otherwise
  properties:           # optional extra driver properties
    useSSL: false
```

Write SQL that runs on both: use `db.dialect()` for the statements that differ (see below), and
column types both understand (`VARCHAR`, `INTEGER`, `BIGINT`, `DOUBLE`, `BOOLEAN`, `TEXT`).

## Schema migrations

Declare every schema change once, numbered. Each runs exactly once, in order, and is recorded in a
`moonlib_migrations` table (per plugin, so several plugins can share one database):

```java
db.migrations()
  .add(1, "create balances",
       "CREATE TABLE balances (uuid CHAR(36) PRIMARY KEY, tokens BIGINT NOT NULL DEFAULT 0)")
  .add(2, "add multiplier", "ALTER TABLE balances ADD COLUMN multiplier DOUBLE NOT NULL DEFAULT 1")
  .addResource(3, "gangs", "migrations/003_gangs.sql")        // a file in src/main/resources
  .add(4, "double old balances", tx -> tx.update("UPDATE balances SET tokens = tokens * 2 WHERE tokens < ?", 100))
  .run();
```

- **Never edit a migration that has shipped**; add a new one. Servers that already ran it would not
  see the change.
- A string may hold several statements separated by `;` (quotes and comments are handled).
- A failed migration is rolled back where the engine allows it, logged, and stops the remaining
  ones; the next start tries again. (MySQL commits DDL immediately, so a failed `ALTER` there cannot
  be undone. Keep each MySQL migration to one DDL statement.)
- Running migrations at startup and waiting for them is fine:
  `db.async(d -> d.migrations()...run()).join();`

## Queries

Every method uses a prepared statement: write `?` where a value goes and pass the values after.
**Never concatenate player input into SQL.**

```java
int changed = db.update("UPDATE balances SET tokens = tokens + ? WHERE uuid = ?", 50, uuid);

Optional<Long> id = db.insert("INSERT INTO gangs (name, owner) VALUES (?, ?)", "Moon", uuid);

List<Gang> gangs = db.query("SELECT * FROM gangs WHERE level >= ?",
        row -> new Gang(row.getLong("id"), row.getString("name"), row.getUuid("owner")), 5);

Optional<Gang> gang = db.queryOne("SELECT * FROM gangs WHERE name = ?", Gang::fromRow, name);

long total = db.value("SELECT SUM(tokens) FROM balances", Long.class).orElse(0L);
boolean known = db.exists("SELECT 1 FROM balances WHERE uuid = ?", uuid);

db.batch("UPDATE balances SET tokens = ? WHERE uuid = ?", rows);   // one round trip, one transaction
```

Parameters may be any JDBC type, plus `UUID` (stored as text), enums (by name), `Instant` and
`Date` (timestamps). `Row` reads them back: `getUuid` (text or `BINARY(16)`), `getEnum`,
`getInstant`, `getString`, `getInt`, `getLong`, `getDouble`, `getBoolean`, `getBytes`, `isNull`.

## Never block the server thread

The methods above block until the database answers. That is fine on an async thread, but on the
server thread it freezes the game, so MoonLib logs a one-time warning naming the statement if you
do. Use the async variants, which run on the database's own worker threads:

```java
db.queryOneAsync("SELECT tokens FROM balances WHERE uuid = ?", row -> row.getLong("tokens"), uuid)
  .thenAccept(tokens -> Tasks.entity(player, () ->               // back on the player's thread
          player.sendMessage("Tokens: " + tokens.orElse(0L))));

db.updateAsync(upsert, uuid, tokens);                              // fire and forget
```

| Async method | Result |
|---|---|
| `updateAsync`, `insertAsync`, `batchAsync`, `transactionAsync` | failures are always logged |
| `queryAsync`, `queryOneAsync`, `valueAsync`, `transactionResultAsync` | handle failures yourself (`exceptionally`) |
| `async(db -> ...)` | run any blocking work on a database thread |

Futures complete on a database thread: hop back with `Tasks.sync` or `Tasks.entity` before touching
players or the world.

## Transactions

```java
db.transaction(tx -> {
    long balance = tx.value("SELECT tokens FROM balances WHERE uuid = ?", Long.class, buyer).orElse(0L);
    if (balance < price) {
        throw new IllegalStateException("not enough tokens");     // rolls everything back
    }
    tx.update("UPDATE balances SET tokens = tokens - ? WHERE uuid = ?", price, buyer);
    tx.update("UPDATE balances SET tokens = tokens + ? WHERE uuid = ?", price, seller);
});

Receipt receipt = db.transactionResult(tx -> ...);                 // returns a value
db.transactionAsync(tx -> ...);                                    // off the server thread
```

Inside a transaction, `tx.nested(inner -> ...)` undoes only its own part (a savepoint) if it throws.

## Dialect helpers

```java
db.dialect().autoIncrementKey()             // INTEGER PRIMARY KEY AUTOINCREMENT / BIGINT ... AUTO_INCREMENT
db.dialect().upsert("balances", "uuid", "tokens", "multiplier")
// SQLite/PostgreSQL: INSERT ... ON CONFLICT (uuid) DO UPDATE SET tokens = excluded.tokens, ...
// MySQL/MariaDB:     INSERT ... ON DUPLICATE KEY UPDATE tokens = VALUES(tokens), ...
db.dialect().insertIgnore("balances", Arrays.asList("uuid", "tokens"))
Dialect.placeholders(3)                     // "?, ?, ?" for IN (...) lists
```

## Tuning and diagnostics

```java
Database db = Database.builder(this)
        .url("jdbc:mysql://db.internal:3306/prison")
        .credentials("prison", secret)
        .poolSize(10)                 // connections, and async worker threads
        .acquireTimeout(10_000)       // ms to wait for a free connection before failing
        .maxLifetime(30 * 60_000)     // replace connections before MySQL's wait_timeout
        .slowQueryThreshold(500)      // log statements slower than this (ms)
        .property("serverTimezone", "UTC")
        .onConnect("SET time_zone = '+00:00'")
        .name("economy")              // shown in logs and thread names
        .build();

db.statementCount();     // statements run so far
db.openConnections();    // pooled connections currently open
```

The built-in pool validates idle connections before reuse and replaces old ones, which avoids the
classic "connection closed after 8 hours" MySQL error. A connection timeout error means queries are
holding connections too long, or the pool is too small for the load.

## Lifecycle

A database closes automatically when its plugin shuts down MoonLib: queued async work finishes first
(up to 10 seconds), so saves started in `onDisable` are not lost. Call `db.close()` to close it
earlier.

## Raw JDBC

```java
try (Connection connection = db.connection()) {    // closing returns it to the pool
    // anything JDBC can do
}
```
