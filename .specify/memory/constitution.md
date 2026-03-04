<!--
Sync Impact Report

Version change: (none) → 1.0.0

Modified principles:
- (initial ratification) Principles I–V created for OpenAS2

Added sections:
- System Constraints & Technology Standards
- Development Workflow & Quality Gates
- Governance (project-specific)

Removed sections:
- None (template placeholders replaced)

Templates requiring updates:
- .specify/templates/plan-template.md — ✅ aligned; Constitution Check remains generic but must enforce these principles when instantiated
- .specify/templates/spec-template.md — ✅ aligned; no constitution-specific assumptions
- .specify/templates/tasks-template.md — ✅ aligned; references independent, testable user stories consistent with this constitution
- .specify/templates/commands/*.md — ⚠ pending; commands template directory not present in this repository

Deferred TODOs:
- None
-->

# OpenAS2 Constitution

## Core Principles

### I. AS2 Protocol Correctness & Interoperability

- OpenAS2 MUST implement AS2 according to the relevant RFCs and de‑facto industry practices,
  including message signing, encryption, compression, and synchronous/asynchronous MDNs.
- Changes to message handling, HTTP/HTTPS behavior, headers, or MDN processing MUST preserve
  interoperability with common AS2 trading partners and test harnesses.
- Any deviation from established AS2 norms (e.g., non‑standard headers, custom MDN formats)
  MUST be explicitly configurable, documented, and OFF by default.

Rationale: OpenAS2 is a B2B document exchange server; protocol correctness and partner
interoperability are the primary sources of user value.

### II. Message Integrity, Reliability & Traceability

- Every inbound and outbound AS2 message MUST be traceable from receipt through processing,
  storage, MDN handling, and final disposition (success or failure).
- The system MUST ensure integrity and non‑repudiation via appropriate signing, verification,
  and MDN correlation; failures in any of these steps MUST be logged with enough detail to
  diagnose issues without exposing sensitive payload data.
- Message persistence, retries, and error handling MUST be designed so that a process crash,
  node restart, or transient network failure does not silently lose messages or MDNs.

Rationale: Trading partners depend on OpenAS2 for reliable, auditable document exchange where
missing or corrupt messages are unacceptable.

### III. Security by Default

- All network communication for AS2 over HTTP/HTTPS MUST default to secure configurations
  (e.g., strong TLS versions and ciphers, secure cookie and header settings where relevant).
- Key, certificate, and credential management MUST follow least‑privilege principles; secrets
  MUST NOT be logged, checked into source control, or exposed in error messages.
- New features MUST default to the most secure reasonable behavior, even if that requires
  extra configuration for less secure legacy use cases; insecure modes MUST be explicitly
  labeled and documented as such.

Rationale: OpenAS2 frequently carries sensitive business documents; security failures can
directly compromise trading partners.

### IV. Operational Observability & Diagnosability

- The server MUST emit structured, filterable logs for key events: message receipt, send,
  MDN generation/receipt, validation failures, retries, and configuration changes.
- Logging MUST be designed so operators can diagnose issues in production without enabling
  verbose or debug‑only modes in normal operation and without logging full payload contents.
- Metrics and health indicators (where supported by the runtime) SHOULD expose message rates,
  error rates, queue depths, and key subsystem health to operations tooling.

Rationale: Operators need to understand “what is happening now” and “what went wrong” in a
running AS2 server without modifying code or redeploying.

### V. Dependency Minimalism & Stability (NO Extra Dependencies)

- OpenAS2 MUST rely only on the minimum set of runtime and build‑time dependencies required
  to implement AS2 messaging, security, and essential tooling; gratuitous new libraries are
  NOT permitted.
- Before introducing any new dependency, contributors MUST:
  - Demonstrate that the capability cannot be reasonably implemented with existing JDK or
    project libraries, and
  - Evaluate long‑term maintenance, licensing, and security implications.
- Where a dependency is justified, it MUST be version‑pinned via the existing Maven setup,
  with upgrades treated as deliberate, reviewed changes rather than automatic pulls to
  latest.

Rationale: A minimal, stable dependency set reduces attack surface, operational surprises,
and upgrade risk for users running OpenAS2 in long‑lived B2B environments.

## System Constraints & Technology Standards

- OpenAS2 MUST support Java 11 and up, and all server code MUST remain compatible with the
  minimum supported Java version documented in the README.
- The canonical build and packaging tool is Maven (via the existing `pom.xml`), and all
  changes MUST preserve the ability to build, test, and package the application using the
  documented Maven commands.
- Docker and Docker Compose assets (including Web UI images) MUST remain optional;
  they MUST NOT introduce additional mandatory runtime dependencies for users who build
  and run the server directly on a JVM.
- Configuration MUST be file‑based and/or environment‑driven in ways compatible with the
  existing `config.xml` and container environment variable conventions; new configuration
  mechanisms MUST NOT break current deployments without a documented migration path.
- Performance and resource usage changes MUST be evaluated in the context of typical B2B
  workloads (large batch EDI, EDIFACT, XML, or binary payloads) and MUST NOT introduce
  unbounded memory growth or blocking behavior that can stall message processing.

## Development Workflow & Quality Gates

- All feature work and bug fixes MUST include automated tests at an appropriate level
  (unit, integration, or system) for the change being made; critical AS2 flows and regressions
  MUST have explicit test coverage.
- Every change MUST be reviewed by at least one other contributor familiar with AS2 semantics
  and this constitution; reviewers are responsible for enforcing protocol correctness,
  security, and dependency minimalism.
- Backward‑incompatible changes to configuration, APIs, or message behavior MUST be called
  out explicitly in release notes and, where feasible, MUST provide migration aids or
  compatibility modes.
- Documentation (including the main README and relevant docs in the `docs` folder) MUST be
  updated when user‑visible behavior, configuration, or operational guidance changes.
- Releases MUST be produced via the documented Maven and Docker workflows so that artifacts
  are reproducible and traceable to specific source revisions.

## Governance

- This constitution supersedes ad‑hoc practices for the OpenAS2 project; when in conflict,
  the constitution’s principles and constraints take precedence.
- Amendments to this constitution MUST:
  - Be proposed in a documented change (e.g., pull request) that explains the motivation
    and impact.
  - Be reviewed and approved by maintainers familiar with AS2, security, and operations.
  - Include an updated version number and `Last Amended` date.
- Versioning for this constitution follows semantic versioning:
  - MAJOR: Backward‑incompatible governance changes or removal/redefinition of principles.
  - MINOR: New principles or sections added, or materially expanded guidance.
  - PATCH: Clarifications, wording fixes, or non‑semantic refinements.
- All project plans, specifications, and task lists generated from `.specify` templates
  MUST include a “Constitution Check” step or equivalent, ensuring:
  - AS2 protocol correctness and interoperability are preserved.
  - Message integrity, reliability, and traceability are not weakened.
  - Security defaults are not compromised.
  - Observability remains sufficient for production diagnosis.
  - No unnecessary dependencies are introduced.

**Version**: 1.0.0 | **Ratified**: 2026-03-04 | **Last Amended**: 2026-03-04
