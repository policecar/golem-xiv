# Golem XIV — Language & Rewrite Decision Memo

> **Status:** decision record (informs a future rewrite/extension; not yet normative).
> **Companion:** `docs/SPEC-ANALYSIS.md` (the reverse-engineered spec + completeness estimate).
> **Purpose:** capture the conclusions of the language-choice investigation so they
> survive context resets and can be executed against directly.

---

## 1. Framing: there are *two* "LLM-native language" questions, and they conflict

The investigation started from "what language is closest to an LLM's inner workings —
least translation layer — *not* what it has seen most." That reframing is sound, but it
answers only one of two questions:

- **Execution fit** — the model writing **GolemScript at runtime**. Optimized by:
  pure/local semantics, **type-first signatures as autoregressive CoT scaffolding**,
  a tight **repair gradient** (typed holes, total checkers), **redundancy over terseness**,
  and **explicit, interceptable effects**. The binding constraint for LLM code reliability
  is the `failure → understand → repair` interface, *not* generation fluency (frontier
  models are already fluent at everything).
- **Development fit** — the model writing the **harness, server, UI, persistence** week
  after week. Brutally favors mainstream ecosystems: in low-resource languages models
  hallucinate APIs constantly, and a thin library ecosystem compounds a thin training
  signal. This is the project's *actual* binding constraint (effectively single-maintainer,
  AI-assisted — see commit history + `CLAUDE.md`).

The earlier survey optimized execution fit and quietly let it dominate. For a system
someone is trying to *extend*, **development velocity wins**, because it gates everything else.

## 2. The language survey (conclusions, condensed)

Two simplices that rarely coincide in one language:

- **Cognitive-fit:** proof · effects+code-as-data · ergonomics/feedback
- **Systems:** runtime-agency · embeddability/sandbox · native-speed/maturity

Frontrunners *for execution fit* (the "most interesting" answers):

| Language | Why it's a frontrunner | Main knock |
|---|---|---|
| **OCaml 5** | effect *handlers* (harness = handler) + HM feedback + F\* bridge + native; good at every corner at once | not pure; effects untyped-yet; smaller ecosystem |
| **Lean 4** | dependent proof + typed holes + macros — the auto-science ceiling | steep surface, weak IO/effects, thin ecosystem |
| **Flix** | Datalog (memory queries) + algebraic effects + HM types fused | tiny ecosystem |
| **Gleam** | BEAM runtime-agency + HM types + tiny grammar | no macros (no code-as-data) |
| **Unison** | content-addressed **code-as-memory** + abilities | own runtime (not embeddable), immature |
| **Roc** | best repair-gradient ergonomics + platform effects | no verification; young |

Notable late additions worth remembering: **Verse** (functional-logic + transactional
shared persistent state — conceptually the closest to golem-xiv's parallel-cognitions +
`memory.remember` transaction model), **Flix** (above), **Pony** (actors + capability
types), **Coalton/Carp** (typed Lisps that escape the homoiconicity trap), **Effekt**
(typed effect handlers), **Dafny** (accessible SMT verification).

Instructive rejects: **Python/SQL-as-prose** (fluency trap — max emit, min verify),
**APL/K** (anti-redundancy), **Forth/Factor** (hidden-stack = anti-locality),
**raw Lisp/Scheme** (homoiconicity trap — keep the idea via Racket `#lang`/typed Lisps,
drop the parens), **Scala 3** (too-large grammar → unconstrainable).

## 3. Decision

**Decouple the harness language from GolemScript.** The survey's frontrunners answer
"closest to the model's inner workings"; they are the *wrong* answer to "what should
golem-xiv be rewritten in." Concretely:

1. **Harness → TypeScript on Deno** (fallback: Rust if security/perf becomes sacred).
   - Sandboxing of model-generated code is **built into the runtime** (permission model) —
     directly fixes the project's biggest unsolved problem (unwired `Shell`, in-process
     execution).
   - V8 isolates → cheap parallel cognitions; one language spans server + the existing web UI;
     first-class LLM SDK + embedding/vector ecosystem.
   - It is the ecosystem where **LLM-assisted development is most productive today** — i.e.
     it optimizes the loop that actually advances this project.

2. **GolemScript → a small constrained dialect we design, not a language we adopt.**
   - Achieve the "LLM-native" properties through the **harness**, not someone else's type
     system: tiny grammar + **grammar-constrained decoding** (hallucinated syntax becomes
     impossible), capabilities injected by name exactly as today (`mind`, `memory`, `files`,
     `http`), effect interception at the **sandbox boundary** (Deno permissions / WASM)
     rather than via algebraic-effect *types*.
   - This is the "hand-roll the 20% of Koka/Unison we actually need" path the Rust+WASM
     option already conceded is viable.

3. **Verification → Lean 4 as a sidecar, not a substrate.** Capture most of the
   auto-science value by letting cognitions *emit* Lean for claims that warrant proof and
   having the harness check it — without betting the codebase on the steepest surface.

4. **Do not rewrite yet — and don't treat Kotlin as a straw man.** The spec analysis found
   the **core loop ~80% done and best-tested where it's riskiest**; the *vision* gaps
   (autonomous memory / GEEP-0001, multi-provider, productionized recursion) are
   **architecture work, not language work** — none is blocked by Kotlin, whose in-process
   script eval is genuinely elegant.

## 4. Sequenced plan (what "go ahead" executes)

**Phase 0 — Conformance suite first (prerequisite for *any* target).**
Convert the implicit spec into language-neutral, key-free fixtures (see `SPEC-ANALYSIS.md` §1):
- `spec/` — JSON Schemas for the domain model (`Cognition`/`PhenomenalExpression`/
  `Phenomenon{Text,Image,Document,Intent,Fulfillment}`/`EpistemicAgent`), the
  `CognitionEvent` stream schema (the load-bearing wire contract), the graph shape, and an
  EBNF for the Golem Markup Language.
- `conformance/` — markup-extraction corpus, **replay transcripts** (recorded Claude streams
  → deterministic event-decoding tests), golden graphs, serialization vectors.
- Seed these by lifting assertions from the existing Kotlin tests in `golem-xiv-core`
  (script extraction) and `golem-xiv-json`.

**Phase 1 — Deno harness spike.** Implement the `reason → intent → execute → loop` core
against the conformance suite: a **replay Cognizer** (deterministic), the markup/intent
extractor, the event-stream accumulator, and an in-memory repository. Goal: pass the same
L0–L2 fixtures the Kotlin original would, with model-generated code running in a permissioned
sandbox.

**Phase 2 — GolemScript dialect.** Define the constrained DSL + grammar-constrained decoding;
inject `mind`/`memory`/`files`/`http` by name; mediate effects at the sandbox boundary.

**Phase 3 — Memory + verification.** Wire a graph/vector backend (Neo4j or Postgres+pgvector)
behind the Memory contract; add the Lean proof sidecar for auto-science claims. Begin
GEEP-0001 (async consolidation) as architecture, language-independent.

**Decision gate:** Phase 1 lets the Deno prototype compete against the Kotlin original on the
**same conformance evidence** — choose to continue the port or stay on Kotlin based on results,
not taste.

## 5. One-line summary

The frontrunners (OCaml 5, Lean, Flix, Unison) are the right answer to *"what is closest to
the model's inner workings"* and the wrong answer to *"what should we rewrite golem-xiv in."*
For the rewrite: **Deno/TypeScript harness + a custom grammar-constrained GolemScript dialect
+ Lean as an optional proof sidecar + the conformance suite before any of it.** Ship the most
*survivable* design, not the most interesting one.
