# Conservation Program Sync Lab

A bounded Spring Boot/PostgreSQL lab for importing synthetic conservation program records from CSV. It demonstrates source-ID idempotency, timestamp precedence, and rejection quarantine. It is a hypothetical portfolio project, not a production integration.

## Run the disposable demo

Prerequisites: Node.js 24/npm for the UI, JDK 17 for the configured Java toolchain, a compatible Gradle runtime JDK (JDK 21 works), and a running Docker-compatible engine. Initial builds download from npm's registry and Maven Central; no global frontend tooling is required.

From the repository root:

```sh
npm ci --prefix frontend
npm run build --prefix frontend
read -s 'MEADOWLINE_DEMO_PASSWORD?Choose a temporary local demo password: '
export MEADOWLINE_DEMO_PASSWORD
./gradlew bootTestRun --console=plain
```

The password prompt above uses zsh (macOS default). On another shell, set `MEADOWLINE_DEMO_PASSWORD` securely in the environment without saving it in repository files. Startup fails if it is blank. The single in-memory account is `demo`, with a Spring-encoded password. This is a local shared synthetic workspace, not user provisioning or an approved steward/manager permission model.

Open `http://127.0.0.1:8080/`, choose **Sign in**, and use `demo` plus your temporary password. Spring Security renders/processes the login form and establishes the server session. Upload `examples/demo-import.csv`:

- First upload on fresh data: received 2, created 1, updated 0, unchanged 0, rejected 1.
- Repeat upload: received 2, created 0, updated 0, unchanged 1, rejected 1.
- The rejected row is quarantined. Each completed upload creates a separate receipt; one canonical record remains.
- Refresh the browser, revisit **Completed history**, and use **View receipt** to read a prior result. History has 20 receipts per page, newest first. **Sign out** invalidates the session.

The existing launcher starts disposable PostgreSQL 17 Alpine, supplies its connection through Testcontainers, and applies Flyway migrations. Keep its terminal open. Data survives browser refresh but disappears when that disposable database is removed. No external datasource or real data should be used for this demo.

Build the UI again after frontend edits; there is no separate cross-origin development server. `processResources` copies `frontend/dist` into the application's static resources. For a packaged local artifact, run the UI build first and then `./gradlew bootJar`; normal application startup still needs a PostgreSQL datasource and the demo password.

## Session and API protection

The app binds to loopback (`127.0.0.1`). API reads, uploads, session details and CSRF-token retrieval require the server-enforced `DEMO_USER` role. Anonymous API reads return HTTP 401; authenticated users without that role receive 403. Unsafe requests without CSRF tokens can receive 403 before authentication is evaluated. The React page is public but cannot read API data before sign-in. Existing POST success bodies, Location headers, and import semantics are unchanged; authentication and CSRF are now required for API clients.

Spring Security's session-backed CSRF protection remains enabled, including login/logout. The browser fetches `/api/csrf` after sign-in and sends its token in the provided header for upload/logout; it lets the browser generate multipart boundaries. Session cookies are HttpOnly with SameSite=Strict; local HTTP is for synthetic testing only. TLS, real account lifecycle, per-user receipt ownership, rate limiting and production operations are not provided. Do not publish this demo or reuse real credentials. All demo-account receipts share one synthetic workspace.

New authenticated endpoints:

- `GET /api/session`: current username; 401 after logout/expiry.
- `GET /api/csrf`: `headerName` and current masked `token`; never persist or log tokens.
- `GET /api/imports?offset=0`: `{receipts: [...], hasMore: boolean}`, at most 20 receipt DTOs, sorted by completion time then ID descending. Nonnegative integer offsets; use 20, 40, etc. for later pages. Concurrent uploads can shift offset pages; this slice does not promise snapshot pagination.

No raw CSV, filename or source identifiers are added to history. Missing/expired-session and failed-request messages are visible in the UI; retry/history refresh controls remain available.

## CSV and API contract

`POST /api/imports` consumes `multipart/form-data` with a required `file` part. The parser reads UTF-8 RFC 4180 CSV, uses the first row as headers, and trims values. Required case-sensitive header names (accepted in any order; surrounding whitespace is trimmed as before):

```csv
source_id,region_code,status,area_hectares,source_updated_at
```

- `source_id`: unique canonical identifier, up to 100 characters.
- `region_code`: required canonical value, up to 10 characters; no region allowlist is implemented.
- `status`: one of `PLANNED`, `ACTIVE`, `COMPLETED` (case-sensitive).
- `area_hectares`: decimal number stored as PostgreSQL `NUMERIC(12,2)`.
- `source_updated_at`: offset date-time, for example `2026-08-05T14:00:00Z`.

Before reading data rows, the parser checks that every required header appears exactly once and there are no blank or extra names. Missing, duplicate, blank or extra names reject the whole file with HTTP 400, before the import service runs: neither canonical nor quarantine data is written. An empty file has no required headers and is also rejected. A valid header-only file retains HTTP 201 with all counters zero.

Header errors use a minimal JSON response chosen for this milestone (no prior custom error format existed):

```json
{"code":"INVALID_CSV_HEADERS","message":"CSV headers must contain source_id, region_code, status, area_hectares, and source_updated_at exactly once, in any order, with no blank or extra names."}
```

This is a file-structure error, distinct from a malformed row value under valid headers: malformed areas/timestamps still use the existing quarantine behavior and successful response counters. The header check does not change numeric/status/version rules or define a general error contract for broken CSV syntax.

A new source ID is created. An existing ID updates only when the incoming timestamp is strictly newer. Equal or older timestamps count as unchanged, even when other fields differ. Counters describe received rows, created records, updated records, unchanged records, and rejected rows.

Unsupported statuses, malformed decimals, and malformed timestamps are quarantined with a reason while valid rows continue. One upload is a single transaction: an unexpected database/runtime error rolls back its canonical and quarantine writes. Parsing happens before this transaction. This is row rejection for the explicitly handled cases, not a guarantee that every invalid input can be imported partially.

## Completed import receipts

Every completed CSV upload now saves an independent receipt in the same transaction as its canonical/quarantine writes. A completion may include rejected rows: the receipt records the returned counters, not a claim that all rows were valid. Reuploading creates a new receipt while canonical record idempotency stays unchanged. Invalid headers or an unexpected database failure leave no receipt. Failed attempts, crash recovery and retries are not recorded by this slice.

The POST response remains HTTP 201 with exactly the existing five-counter JSON body. Its additive `Location` header identifies the saved receipt at `/api/imports/{id}`. The signed-in React UI follows that link automatically. API clients must supply an authenticated session cookie and valid CSRF header for writes; unauthenticated curl examples from earlier lab versions no longer work.

`GET /api/imports/{id}` returns HTTP 200 with this shape (values illustrative):

```json
{"id":"f2f3e5d8-f1ed-4f98-85bf-8b6305dd48f0","completedAt":"2026-10-30T04:00:00Z","result":{"received":2,"created":1,"updated":0,"unchanged":0,"rejected":1}}
```

`completedAt` is the database timestamp when the receipt is inserted after row processing, before transaction commit. It survives only if that transaction commits. The receipt stores no filename, raw CSV, source IDs, or personal data. An unknown well-formed UUID returns HTTP 404 with `{"code":"IMPORT_RECEIPT_NOT_FOUND","message":"No completed import receipt exists for this ID."}`. Malformed UUIDs return HTTP 400 through Spring's existing conversion handling; no custom body is promised for that case.

The lookup and paged history require the demo role, but receipts have no per-user ownership. Use only the local shared synthetic demo; business role/ownership policy and production controls remain future decisions. The default Testcontainers demo is disposable: receipts disappear with its database, while the application itself persists them in PostgreSQL.

## Inspect and clean up the demo

`V1__create_program_record_table.sql` creates `program_record` with a unique `source_id`; `V2__create_program_record_quarantine_table.sql` stores rejected raw values, rejection reasons, and quarantine timestamps. `V3__create_import_receipt_table.sql` adds UUID receipt identity, completion time and the five counters with consistency constraints. All three live under `src/main/resources/db/migration/`.

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

After two uploads, expect one canonical row, two quarantine rows and two receipts on fresh demo data. Inspect receipts with `SELECT * FROM import_receipt ORDER BY completed_at;`. Exit psql with `\q`. Stop the demo with Ctrl-C in its startup terminal; Testcontainers/Ryuk normally removes its disposable containers. If an interrupted run leaves a container behind, verify the exact ID belongs to this demo before removing that container only. Do not prune containers or volumes globally. Restart with a fresh disposable instance to reset the demonstration; no persistent database cleanup is needed.

## Tests and boundaries

```sh
./gradlew test --console=plain
```

Frontend checks: `npm test --prefix frontend` and `npm run build --prefix frontend` (includes TypeScript checking). Backend checks: `./gradlew test`. Security tests use a synthetic password passed explicitly through test annotations, never as a runnable application default.

Tests use Docker-backed PostgreSQL 17 Alpine. The integration tests cover application startup, database source-ID uniqueness, identical/newer/older imports, unsupported statuses, HTTP repeat uploads, mixed uploads with malformed areas/timestamps, rollback of both canonical and quarantine writes after an unexpected database failure, header rejection with no database writes, and receipt persistence/retrieval, reimport, 404 behavior and rollback when the receipt insert itself fails. Header tests also cover reordered/trimmed names, wrong case, empty files, header-only files, and rejection before a broken data row is parsed. Source: `src/test/java/com/jonnathangarcia/conservationsync/`.

Known limits: local-demo authentication/UI only; no real identity provisioning, per-user ownership, deployment setup, quarantine management endpoint, or failed-attempt lifecycle audit. CSV is loaded into memory. Structurally broken CSV, oversized database values, and other unhandled constraint errors have no custom error contract. Blank identifiers/regions, nonnegative area rules, timestamp ties with differing content, and concurrent imports have no comprehensive validation/test coverage. Plain `bootRun` requires an externally configured PostgreSQL datasource; this guide deliberately uses the existing disposable launcher.

The lab is complete when a new reader can run this demo, explain its counters and quarantine behavior, and run the existing suite. New product features are outside this milestone. Historical test results are not a guarantee of the current checkout; use an actual test run for current evidence.
