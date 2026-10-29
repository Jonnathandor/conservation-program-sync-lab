# Working in this repository

Meadowline Program Sync grows from this fictional conservation import lab. Read the relevant code, README, planning decisions, and any more specific instructions before changing it.

- Keep each change bounded and reviewable. Preserve unrelated work; never discard it to simplify a task.
- Explain important tradeoffs in plain language for a user learning the system. Separate implemented behavior, accepted decisions, and proposals.
- Do not guess business rules. Resolve contract questions before silently changing the API or data semantics; see `docs/meadowline-plan.md`.
- Run checks relevant to the change and report actual outcomes, including blocked or skipped checks. Do not claim historical results as a fresh pass. Prose-only changes normally need source/consistency and whitespace review, not a full test run.
- Existing tests: `./gradlew test` (Docker-backed PostgreSQL). Disposable application demo: `./gradlew bootTestRun --console=plain`; follow `README.md` for upload and cleanup. Avoid real or persistent databases for demos/tests.
- Never read, commit, or publish secrets or real organizational data. Use synthetic fixtures.
- Commit, push, and deploy only within the user's authorized scope. Follow the latest instruction; ask when the required scope is missing. No repository rename, dependency upgrade, or infrastructure expansion merely to match a future vision.
