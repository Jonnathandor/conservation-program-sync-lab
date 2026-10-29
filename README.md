# Conservation Program Sync Lab

A bounded Spring Boot/PostgreSQL lab for importing synthetic conservation program records from CSV. It demonstrates source-ID idempotency, timestamp precedence, and rejection quarantine. It is a hypothetical portfolio project, not a production integration.

## Run the disposable demo

Prerequisites: JDK 17 for the configured Java toolchain, a JDK compatible with Gradle 9.5.1 to run the wrapper (JDK 21 works), Docker running with permission to start containers, curl, and internet access for initial dependency/image downloads. No separate Gradle installation is required.

From the repository root:

```sh
./gradlew bootTestRun --console=plain
```

The existing `TestConservationProgramSyncLabApplication` launcher imports `TestcontainersConfiguration`, which starts `postgres:17-alpine` and supplies the connection through `@ServiceConnection`. Flyway creates the schema. Wait for the application startup message; the default HTTP port is 8080. Use a fresh disposable database and no external datasource overrides for the expected results below. Keep this terminal running.

In a second terminal at the repository root:

```sh
curl --fail-with-body -X POST http://localhost:8080/api/imports \
  -F 'file=@examples/demo-import.csv'
```

Expected HTTP 201 response on fresh data (JSON field order is immaterial):

```json
{"received":2,"created":1,"updated":0,"unchanged":0,"rejected":1}
```

Run the same upload again:

```json
{"received":2,"created":0,"updated":0,"unchanged":1,"rejected":1}
```

The valid source ID exists once. The malformed timestamp row creates a new quarantine entry on every upload; quarantine itself is not deduplicated.

## CSV and API contract

`POST /api/imports` consumes `multipart/form-data` with a required `file` part. The parser reads UTF-8 RFC 4180 CSV, uses the first row as headers, and trims values. Required exact header names:

```csv
source_id,region_code,status,area_hectares,source_updated_at
```

- `source_id`: unique canonical identifier, up to 100 characters.
- `region_code`: required canonical value, up to 10 characters; no region allowlist is implemented.
- `status`: one of `PLANNED`, `ACTIVE`, `COMPLETED` (case-sensitive).
- `area_hectares`: decimal number stored as PostgreSQL `NUMERIC(12,2)`.
- `source_updated_at`: offset date-time, for example `2026-08-05T14:00:00Z`.

A new source ID is created. An existing ID updates only when the incoming timestamp is strictly newer. Equal or older timestamps count as unchanged, even when other fields differ. Counters describe received rows, created records, updated records, unchanged records, and rejected rows.

Unsupported statuses, malformed decimals, and malformed timestamps are quarantined with a reason while valid rows continue. One upload is a single transaction: an unexpected database/runtime error rolls back its canonical and quarantine writes. Parsing happens before this transaction. This is row rejection for the explicitly handled cases, not a guarantee that every invalid input can be imported partially.

## Inspect and clean up the demo

`V1__create_program_record_table.sql` creates `program_record` with a unique `source_id`; `V2__create_program_record_quarantine_table.sql` stores rejected raw values, rejection reasons, and quarantine timestamps. Both live under `src/main/resources/db/migration/`.

Find the PostgreSQL container started for this run from the Testcontainers startup log. Verify its ID against `docker ps` and use only that disposable container, never an unrelated database:

```sh
docker exec -it <demo-postgres-container-id> psql -U test -d test
```

The existing container configuration uses Testcontainers' default disposable database/user (`test`). In psql:

```sql
SELECT source_id, region_code, status, area_hectares, source_updated_at
FROM program_record WHERE source_id = 'SYN-DEMO-VALID-001';
SELECT source_id, source_updated_at, rejection_reason
FROM program_record_quarantine WHERE source_id = 'SYN-DEMO-BAD-TIME-001';
```

After two uploads, expect one canonical row and two quarantine rows. Exit psql with `\q`. Stop the demo with Ctrl-C in its startup terminal; Testcontainers/Ryuk normally removes its disposable containers. If an interrupted run leaves a container behind, verify the exact ID belongs to this demo before removing that container only. Do not prune containers or volumes globally. Restart with a fresh disposable instance to reset the demonstration; no persistent database cleanup is needed.

## Tests and boundaries

```sh
./gradlew test --console=plain
```

Tests use Docker-backed PostgreSQL 17 Alpine. The 11 test methods cover application startup, database source-ID uniqueness, identical/newer/older imports, unsupported statuses, HTTP repeat uploads, mixed uploads with malformed areas/timestamps, and rollback of both canonical and quarantine writes after an unexpected database failure. Source: `src/test/java/com/jonnathangarcia/conservationsync/`.

Known limits: no UI, authentication, deployment setup, quarantine management endpoint, or durable import audit. CSV is loaded into memory. Missing headers, structurally broken CSV, oversized database values, and other unhandled constraint errors have no custom error contract. Blank identifiers/regions, nonnegative area rules, timestamp ties with differing content, and concurrent imports have no comprehensive validation/test coverage. Plain `bootRun` requires an externally configured PostgreSQL datasource; this guide deliberately uses the existing disposable launcher.

The lab is complete when a new reader can run this demo, explain its counters and quarantine behavior, and run the existing suite. New product features are outside this milestone. Historical test results are not a guarantee of the current checkout; use an actual test run for current evidence.
