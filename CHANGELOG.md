# Changelog

Releases of the amaise agent SDK and the agent examples. They ship on one version
and one tag, so one log covers both.

Each entry says plainly whether it needs anything from you. **Action required**
marks a change an unmodified integration will notice; everything else is safe to
take by bumping the version.

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
