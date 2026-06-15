# Phase 1 — Conformance harness (1a) + Deno spike (1b)

> **Status:** delivered artifacts.
> **Companion:** `docs/LANGUAGE-DECISION.md` §4, `docs/PHASE-0-CONFORMANCE-KIT.md`.

Phase 1 executes the language decision's plan in two steps, both run **green** in the build
container (no API key, no model — fully deterministic).

## Phase 1a — kit verified against the Kotlin reference (in this repo)

A conformance harness lives in the main project and runs the Phase 0 kit's fixtures against
the **real** Kotlin implementation:

- `golem-xiv-core/src/test/kotlin/conformance/ConformanceKitTest.kt`
- fixtures vendored under `golem-xiv-core/src/test/resources/conformance/`

```bash
./gradlew :golem-xiv-core:test --tests "com.xemantic.ai.golem.conformance.ConformanceKitTest"
```

4 tests, all passing. It checks:

1. **markup extraction** — `extractGolemScripts()` reproduces the corpus, chunk-invariantly;
2. **impediment envelope** — `GolemScriptExecutor` emits the exact `<golem:impediment phase>`
   reference output;
3. **serialization** — every vector round-trips through the production `golemJson`. This is
   the load-bearing check: it **confirms** the kit's trickiest claims against the real
   kotlinx serializer — the fully-qualified-name discriminator for `EpistemicAgent.*` and
   the omitted defaults (`impeded`, `culminationMoment`, `recursiveCognitionId`);
4. **intent decode** — the tool-use transcript's `input_json` deltas decode through the real
   `IntentCognizer` to exactly the expected Intent purpose/code events.

This turns the kit from "internally consistent" into "verified against the reference" — so
any port builds on trusted evidence.

## Phase 1b — Deno/TypeScript harness spike (standalone repo, shipped as a bundle)

An independent Deno/TypeScript implementation of the decode/persist path that passes the
**same** L0–L2 fixtures. Delivered as a git bundle for import into its own repo:

```
spec-kit/golem-xiv-harness.bundle
```

```bash
git clone spec-kit/golem-xiv-harness.bundle golem-xiv-harness
cd golem-xiv-harness
deno task test        # 11 passed | 0 failed
```

It implements: the wire codec, the `<golem-script>` extractor, a streaming JSON parser, the
intent decoder, a deterministic replay Cognizer, an event accumulator, and a memory-graph
builder with an isomorphism check against the golden graph. Intent **execution is stubbed**
(running model-generated code in a permissioned sandbox is the Phase-1 stretch; the
constrained GolemScript dialect is Phase 2). See the harness's own `README.md`.

## Decision-gate status

The same language-neutral conformance evidence now passes against **both** the Kotlin
reference and an independent TypeScript port — exactly the basis the memo wanted for
choosing "continue the port vs. stay on Kotlin" by evidence rather than taste. The next
real fork (Phase 2: the grammar-constrained GolemScript dialect + a permissioned execution
sandbox) is where the port would start to *diverge from* — and potentially beat — the
Kotlin original.
