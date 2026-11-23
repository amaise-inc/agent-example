# Changelog

Releases of the amaise agent SDK and the agent examples. They ship on one version
and one tag, so one log covers both.

Each entry says plainly whether it needs anything from you. **Action required**
marks a change an unmodified integration will notice; everything else is safe to
take by bumping the version.

## 1.21.0

### Action required

- **Update the JVM that runs your agent or the SDK if it is Java 17.0.6 or later on x86-64.**
  CVE-2026-114938 lets a JVM on x86-64 corrupt memory while it decodes Base64 input. Update to
  Java 17.0.21.1, 21.0.13.1, 25.0.5.1 or 27.0.1.1, or your JVM vendor's equivalent build. The
  Spring and Quarkus example images now build on fixed versions
  (`eclipse-temurin:25.0.5.1_2-jdk`, `ubi9/openjdk-25:1.24-5`). If you built an example image
  before 2026-11-21, rebuild it with `docker compose build --pull` or `make dockerize`, which
  now pulls, and recreate the containers. Until you can update, start the JVM with
  `JAVA_TOOL_OPTIONS=-XX:-UseBASE64Intrinsics`. Not affected: Java 11, ARM64 (aarch64) hosts,
  Quarkus native images, and integrations without a JVM such as the Node.js example.
- **A client generated from the OpenAPI document renames `TicketClosedEvent` to
  `TicketUpdatedEvent`.** The schema is now published under its own name. The event payload and
  the `TICKET_UPDATED` type are unchanged, but code that refers to the generated
  `TicketClosedEvent` class stops compiling once you regenerate. Rename it to
  `TicketUpdatedEvent`.
- **A dashboard `list` answer leaves out the rows a person excluded in amaise.** `items` and
  `_meta.rowCount` in the answer text count only the remaining rows, and `_meta.excludedRowIndices`
  is gone. This is behaviour of the API, already live for every SDK version. If you counted or
  filtered excluded rows yourself, drop that step.

### Deprecated

- **Every collaboration request is now a chat, so `AgentTicketDTO.coordinator()` and the `REVIEWING`
  status are deprecated.** Both keep working and nothing needs to change. `coordinator()` returns
  the person who coordinated a request before it became a chat, and `null` for every other request,
  which is every request created from now on; that person is also listed in `recipients()`, and on a
  converted request that had no expert yet also in `recipient()`, which used to be `null` there.
  `REVIEWING` is no longer set, and no `TicketUpdatedEvent` follows a review any more. `QUEUED`
  keeps its meaning: nobody has taken the request on yet, for example at an external service
  provider. See `BREAKING_CHANGES.md` for the field-by-field change.
- **Behaviour change ahead: three months after the EU release of this change,
  `AgentTicketDTO.coordinator()` returns `null` for every request.** amaise then stops recording who
  coordinated a request before it became a chat. The field stays; that person stays listed in
  `recipients()`. If you read `coordinator()`, read `recipients()` instead before then.

### New

- Every new collaboration request is a chat, so its question and answer text arrive in
  `ticketEventHistory`. `AgentTicketDTO.question` and `TicketCreatedEvent.question` are empty for a
  new request; its opening message is the first `MESSAGE` entry. When someone marks the chat as
  answered, a `TicketUpdatedEvent` with `status` `ANSWERED` delivers the answer:
  `AgentTicketDTO.answer` carries the chat marker `TICKET_TYPE:CHAT`, the answer text is a `MESSAGE`
  entry before the `ANSWER` entry, and `attachments` lists the files exchanged. Later events for the
  same request keep `status` `ANSWERED`, so import an answer once per `ANSWER` entry `id`. Requests
  created earlier keep their question and answer text. This is behaviour of the API and reaches
  every SDK version. No action is needed.
- A collaboration request can name several recipients. `AgentTicketDTO.recipients()` lists
  every recipient in the order they were named; `recipient()` is unchanged and equals the first
  entry. The ticket event history gains `ADD_RECIPIENT` and `REMOVE_RECIPIENT` entries when a
  recipient joins or leaves a request.
- A dashboard read names the run its answers come from: `AgentDashboardDTO.runId()` and
  `runCompletedAt()`. Each answer says whether a person corrected it (`corrected()`) and when a
  person signed it off (`signedOffAt()`). The new fields are additive; `runId`,
  `runCompletedAt` and `signedOffAt` are `null` when there is nothing to report.

### Improved

- Deleting a message or an attachment in a collaboration request withdraws its files: amaise
  sends a `TicketUpdatedEvent` whose `attachments` and history leave them out, so you can drop
  your copy, and their URIs answer `404`. Before, a deleted message's files stayed listed and
  no event followed the delete. No action is needed.
- Dependency updates: the SDK runtime moves to OpenFeign 13.17, Jackson 2.22.4,
  Immutables 2.12.3 and swagger-annotations 2.2.55, all still on the Java 11 floor. The
  examples move to Quarkus 3.40.1.1 and the Maven wrapper to Maven 3.10.0.
- A notebook's `content` is now normalised HTML when it is saved. amaise also records who wrote
  each passage as `data-*` attributes; those stay on the server and are stripped from every
  notebook an agent reads. No action is needed.
- The examples' health and metrics port accepts less: the Spring example no longer parses
  multipart requests (`spring.servlet.multipart.enabled=false`) and the Quarkus example no longer
  offers HTTP/2 (`quarkus.http.http2=false`). Health checks and Prometheus scrapes are unchanged.
  If you added your own endpoints to an example that need either, turn the setting back on.

## 1.20.0

### Action required

Both changes below are behaviour of the API itself and have been live for every
SDK version since they shipped; this release is the SDK documenting them.

**Updating a case never changes its status.** `PATCH /agents/v1/cases/{legalCaseId}`
(`LegalCaseService.update`, `updateLegalCase` in the Node example) used to persist
the `status` on the request body, so an update on an archived case silently
reopened it. The status now changes only through `archive` / `unarchive`. An
update that sends a status still receives `200`, and the case keeps its stored
status; the response reflects the persisted case rather than echoing the request.
If your integration reopened cases by updating them, call `unarchive` first.

**Source-file writes on an archived case answer `409`.** Deleting a file, moving a
file out of an archived case, and updating the metadata of a file in an archived
case are refused with `409` and raise `ArchivedLegalCaseException`. Workspaces
exempt from archived-case immutability are unaffected. A file whose case has been
deleted and not yet purged answers as before.

### Improved

- `SourceFileService.delete` is a soft delete — the file's documents go to the
  trash and it can be restored by creating it again with the same id and an
  identical binary — and it is idempotent on an already deleted file while the
  case is open. It no longer declares `PreconditionFailedException`.
- A PDF that is encrypted for editing but opens without a password (an owner
  password with object streams) is now accepted on upload. A PDF that needs a
  password to open is still rejected, and the error message says so.
- The Node example runs on Node 24 (`.node-version`).

### Fixed

- The release build verifies the artifact on the toolchains the examples need
  (JDK 25, Node 24), so a published release is one that built from a fresh unzip.

## 1.19.0

### Action required

**A list row's citation cell is now `_citations`.** It was `_document_reference`.
The value is unchanged — the row's sources as rendered links in a plain string
cell — but the key is not. The old name was wrong: a citation in that cell can
resolve to a case document, an uploaded asset or an external source, not only a
document.

Read `_citations` and ignore any other reserved cell you find on a row. Stored
answers were moved onto the new key when this change was deployed, so the API has
been serving `_citations` for some time; this release is the SDK catching up. Two
sets of rows are not covered by that move and still arrive on the old key: an
answer that already carried both spellings keeps the old one, and an answer that
reaches a workspace through an import of an export predating the rename arrives
carrying only the old one. If you cannot tolerate either case, read both keys and
prefer `_citations`.

The key was never declared in the OpenAPI contract and a row is an untyped map of
column key to value, so there is no generated-type change to pick up.

### New

- **Research answers.** A dashboard answer can now be of type `research`, carried
  by `AgentDashboardResearchAnswerDTO` as a new subtype of
  `AgentDashboardBaseAnswerDTO`. An integration that switches exhaustively on the
  answer type should add a branch; one that ignores unknown types needs nothing.
- **External sources.** `AgentDashboardExternalSourceDTO` describes a citation
  that resolves to a source outside the case file. External sources render as an
  external link; only `http` and `https` links are accepted. `url` is always
  present; `title` and `sourceName` are null when the provider supplied neither.

### Improved

- A citation the read endpoint cannot resolve — a document deleted since the
  answer was written, or an uploaded asset, which is not agent-resolvable — is
  dropped together with the space it stood in. Previously both surrounding spaces
  were kept, so a dropped citation left a double space mid-sentence or a space in
  front of the full stop. An answer whose citations all resolve, and an answer
  that cites nothing, are byte-for-byte unchanged.
- A source file that fails processing is now reported as failed by both the ready
  mail and the SDK event, instead of one of them reporting success.

### Fixed

- **The release artifact builds.** `sdk-release.zip` did not contain
  `.mvn/maven-wrapper.jar`, so neither Java example could bootstrap `./mvnw` from
  a fresh unzip, and the Node example failed its own `make verify` because the
  formatter linted the client you had just been told to generate. Both are fixed,
  and the artifact is now unzipped and built in CI before it is published.
- The Maven wrapper jars ship with the examples, so a cold checkout no longer
  fails when the wrapper download is rate-limited.
- Dependency updates clearing published advisories in the examples: `joi`
  17.13.7, and `js-yaml` pinned to 4.3.2 through the code generator.

## Earlier releases

Notes for 1.18.3 and earlier are on the
[releases page](https://github.com/amaise-inc/agent-example/releases).
