# Phase 0 — Conformance Kit (handoff)

> **Status:** delivered artifact pointer (the kit itself lives in a *separate* repo).
> **Companion:** `docs/LANGUAGE-DECISION.md` §4 (Phase 0), `docs/SPEC-ANALYSIS.md` §1.

Phase 0 of the language decision calls for a **language-neutral, key-free conformance
suite** that pins the implicit spec *before* any harness (Deno/TypeScript, Rust, or the
existing Kotlin) is built or ported — so the Phase-1 decision gate is settled by
conformance evidence, not taste.

That kit has been built as a **standalone repository**, intended to be imported into a new
private repo **`policecar/golem-xiv-spec`**. It is shipped here as a git bundle so it
survives context resets and the ephemeral build container.

## The bundle

```
spec-kit/golem-xiv-spec.bundle
```

It is a complete, self-contained git history (branch `main`). Import it:

```bash
# Option A — clone the bundle into a fresh working copy, then push to the new repo
git clone spec-kit/golem-xiv-spec.bundle golem-xiv-spec
cd golem-xiv-spec
git remote add origin git@github.com:policecar/golem-xiv-spec.git   # create as PRIVATE first
git push -u origin main

# Option B — verify before importing
git bundle verify spec-kit/golem-xiv-spec.bundle
```

Then sanity-check the kit (no API key, no model needed):

```bash
pip install jsonschema
python3 tools/validate.py        # expect: PASSED: 51 checks.
```

## What's in it

- **`spec/schema/`** — JSON Schemas (Draft 2020-12) for the domain model, the
  `CognitionEvent` stream contract (the load-bearing wire contract), the `GolemOutput`
  envelope, and the memory-graph shape.
- **`spec/grammar/golem-markup.ebnf`** — the Golem Markup Language, both surface dialects
  (`golem:`-namespaced and `<golem-script purpose=…>`).
- **`conformance/`** — markup-extraction corpus + impediment envelope, replay transcripts
  (recorded provider stream → deterministic `CognitionEvent` decoding), a golden graph,
  and serialization vectors.
- **`tools/validate.py`** — proves the kit is internally consistent.

Everything is reverse-engineered from `golem-xiv-core` (script extraction/execution),
`golem-xiv-json` (streaming parser), the `golem-xiv-api` domain model, and the
`golem-xiv-mini` constitution. See the kit's own `NOTICE.md` for provenance and the
serialization gotchas (notably the fully-qualified `EpistemicAgent` discriminator and
omitted defaults).
