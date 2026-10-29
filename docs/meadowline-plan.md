# Meadowline Program Sync: bounded delivery plan

## Purpose and status

Build a fictional conservation data-quality application in this existing repository. The lab is the backend foundation, not a separate codebase to combine or the completed full-stack product. No real organization integration or real data is in scope.

Implemented evidence:

- `src/main/java/com/jonnathangarcia/conservationsync/ProgramImportController.java`: multipart `POST /api/imports` and result counters.
- `ProgramCsvParser.java` and `ProgramImportService.java` in the same directory: CSV ingestion, source-ID uniqueness, updates only for strictly newer timestamps, unsupported-status/malformed-value quarantine, and atomic upload processing.
- `src/main/resources/db/migration/V1__create_program_record_table.sql` and `V2__create_program_record_quarantine_table.sql`: canonical and quarantine storage.
- `src/test/java/com/jonnathangarcia/conservationsync/`: 11 test methods, including rollback of canonical/quarantine writes on unexpected database failure. This is a source count, not a fresh test result for this planning change.
- `README.md` and `examples/demo-import.csv`: reproducible disposable demo and documented boundaries.

There is currently no application sign-in, browser UI, durable import lifecycle/audit, reporting, or outbound delivery.

## Accepted technical direction

Use a modular monolith with Java/Spring, JDBC, Flyway and PostgreSQL, plus a React/TypeScript operational UI. Serve UI and API from the same origin in one application with PostgreSQL. Plan Docker Compose for local development and CI/container demo proof later; none is added by this document. Retain the current toolchain until compatibility or security evidence warrants a change. See [architecture decision](decisions/001-architecture.md).

## Intended user journeys

- Signed-in users upload files and see outcomes and import history.
- Stewards inspect records/exceptions, correct source data, and resubmit with traceable outcomes.
- Managers inspect summaries and data freshness using explicitly agreed definitions.
- Support follows traceable failures and recovery, including mock delivery outcomes.

These journeys guide slices; they do not approve every underlying business rule or every possible feature in the broader vision.

## Milestones and observable acceptance

| Milestone | Observable completion |
| --- | --- |
| 1. Contract validation | Agreed file/row error contract; missing, duplicate, blank or extra column names produce HTTP 400 file-level rejection with no database writes; existing valid CSV behavior stays compatible. |
| 2. Durable lifecycle and audit | Each accepted upload has an ID, durable outcome/counts, and traceable failure; rollback leaves no partial canonical/quarantine writes. Crash/retry state transitions are explicitly designed and tested. |
| 3. First browser vertical slice | A user signs in, uploads the synthetic CSV, reads counters/errors, and finds that upload in history. Server authorization and session/CSRF controls are tested. |
| 4. Record and exception review | Steward can inspect records and reasons and follow correction/resubmission lineage; manager summary/freshness values reconcile with fixtures under agreed definitions. |
| 5. Mock delivery and recovery | Durable mock outbound work demonstrates retries, idempotency and a traceable final outcome without a real external system. |
| 6. Release proof | CI runs relevant checks; accessibility, security/access control and bounded load checks have recorded results; container deployment/demo, recovery and cleanup are reproducible. Public hosting requires suitable controls and explicit cost/deployment approval. |

The next implementation slice is only milestone 1's **CSV header validation**, under the approved contract below. Specify the exact error payload and code names first, then add focused parser/HTTP tests proving rejection before any database writes and regression coverage for valid files and reordered headers. Do not implement all field-validation policies at once or fold UI/audit into this slice.

## Approved CSV header contract (not yet implemented)

- Require all five existing column names in any order: `source_id`, `region_code`, `status`, `area_hectares`, `source_updated_at`.
- Reject missing, duplicate, blank and extra column names with HTTP 400 and a clear error code.
- Reject the whole file before row processing, with no database writes, including no canonical or quarantine writes.
- Preserve successful uploads using the existing contract; accepting reordered valid headers must remain supported.

These semantics are approved. The exact error payload shape and error code names still need specification before implementation. Header normalization details must be explicit rather than silently changing existing parsing behavior.

## Contract decisions still required

Compatibility recommendation: retain `POST /api/imports`, existing counter names, and `PLANNED`/`ACTIVE`/`COMPLETED` unless an explicit contract decision authorizes change.

| Question | Current behavior / decision needed |
| --- | --- |
| Regions | Lab fixtures use `MB`/`SK`; design uses `NORTH`/`CENTRAL`/`SOUTH`. No catalogue validation exists. Choose a catalogue before enforcement. |
| Field rules | Agree required/nonblank semantics, lengths, numeric bounds/precision/rounding, case normalization, UTC/offset handling, and upload byte/row bounds. Database constraints alone are not the proposed product contract. |
| Freshness | Agree threshold, clock, and display/summary definition before reporting. |
| Older/tied versions | Lab counts older/equal timestamps unchanged. Design proposes stale quarantine and equal-time/different-payload conflicts; those changes are unimplemented and require a decision. |
| Same-file duplicate IDs | Rejecting all occurrences is a proposal, not confirmed policy or current behavior. Choose semantics before implementation. |
| Header error representation | HTTP 400 and rejection semantics are approved above. Specify exact payload shape, code names and normalization details before implementation. |

## Scope and design context

Keep discovery items such as case management, GIS, agreements and wider organizational integrations outside this release. Microservices and Kubernetes have separate evidence gates; do not add them for anticipated scale alone.

Meadowline's broader design envisions a fictional conservation data-quality workflow spanning upload, review, reconciliation, reporting and recovery. This document records the selected direction and bounded milestones; it does not include the complete original specification or approve every envisioned feature. The checked-in code is the source of truth for existing behavior; accepted architecture is separate from unresolved business proposals above.
