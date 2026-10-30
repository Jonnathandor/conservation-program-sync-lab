# ADR 001: Modular monolith for Meadowline

Status: Accepted. Implementation remains incremental.

## Context

The existing lab already demonstrates transactional CSV ingestion with Java/Spring, JDBC, Flyway and PostgreSQL. Meadowline is a broader fictional full-stack design, with no separate application repository. We need a teachable, demonstrable system with operational UI and traceability, without paying distributed-system costs before team, deployment or scaling evidence exists.

## Decision

Evolve this repository as a modular monolith. Keep Java/Spring, JDBC, Flyway and PostgreSQL; build a React/TypeScript UI served with the API from the same origin in one application. Plan Docker Compose locally; add CI and container demo/release evidence in later milestones. Do not upgrade the current toolchain as part of planning.

Logical responsibilities are imports, contract validation, records/reconciliation, exceptions, audit, reporting, delivery, and access/operations. These are intended boundaries, not separate services or a mandate to create empty packages now. Test boundaries as they become useful. Compatible Spring Modulith support is an option, not a required dependency.

Keep canonical and quarantine changes atomic for an upload. If durable FAILED audit is added, its write must survive business-transaction rollback: use a separately proxied component or an explicit transaction template with a deliberately chosen separate transaction. Self-invocation of a transactional method does not establish that boundary. Design crash/retry states and reconciliation explicitly; an extra transaction alone does not solve interrupted uploads.

Later mock outbound delivery should use durable outbox work and idempotent processing. Kafka is unnecessary for that initial demonstration. Authentication is planned as same-origin sessions with CSRF protection and server-enforced roles; role/business permission details remain to be agreed. No public demo before appropriate access/security controls and cost/deployment approval.

## Tradeoffs and revisit gates

One application is easier to run, test, deploy and reason about, and preserves database transaction boundaries. The cost is coupled release/scaling and the need to maintain module discipline. UI/API same-origin deployment reduces cross-origin complexity, but requires a deliberate asset build and session/security design.

Do not introduce microservices or Kubernetes in the first release. Revisit service separation only with concrete independently scaling workloads, team ownership, or independent deployment requirements, and an assessment of operational cost. Evaluate Kubernetes separately only after a deployment/operations need justifies its production responsibilities. Neither follows automatically from adding a UI or containers.

Business validation proposals are not architecture decisions. Preserve existing API/status/version behavior until explicitly resolved in [the plan](../meadowline-plan.md).

## Rationale sources

- [Microsoft: Microservices architecture style](https://learn.microsoft.com/en-us/azure/architecture/guide/architecture-styles/microservices): distributed services introduce operational, consistency and communication tradeoffs alongside independent deployment/scaling benefits.
- [Kubernetes: Production environment](https://kubernetes.io/docs/setup/production-environment/): production clusters require explicit availability, access, capacity and operational choices.
