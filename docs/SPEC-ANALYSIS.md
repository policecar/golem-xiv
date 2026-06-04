# Golem XIV — Reverse-Engineered Spec & Completeness Estimate

> **Status:** analysis document (not a normative GEEP).
> **Date:** 2026-06-04.
> **Method:** distilled from the source tree, the two constitutions
> (`GolemXivConstitution.md` and the `golem-xiv-mini` constitution), the
> domain model in `golem-xiv-api`, `GEEP-0001`, the upstream issue tracker,
> and the `xemantic/*` dependency graph. The task framing was: *"assume a
> spec and tests exist of which golem-xiv is a partial implementation —
> distill the spec and estimate how complete it is."* Since no external spec
> document is published, the "spec" below is reconstructed from the code's
> own vocabulary, the system prompts (which **are** the operational spec),
> and the stated roadmap.

---

## Part 1 — The distilled specification

### 1.1 Vision

Golem XIV is an **autonomous metacognitive agent** that performs knowledge
work (code, documents, data, research) by *thinking in code* and reasoning
over a **persistent knowledge-graph memory**. The marketed pillars
(`README.md`) are: cognition over communication, metacognition, unlimited
graph memory, private org-scoped context, mass parallelism, self-modification,
chain-of-**code** (not chain-of-thought-in-English), Unix omnipotence,
auto-science, and LLM-independence.

The defining architectural bet, made explicit in both constitutions, is that
the agent expresses **all** of its actions as **GolemScript** (Kotlin script),
never as conventional LLM "tool calls":

> *"IMPORTANT: never use tools, only GolemScript."* — `golem-xiv-mini`

### 1.2 Core ontology (the formal model)

The system has a deliberate, phenomenology-flavoured vocabulary. The canonical
types live in `golem-xiv-api/src/commonMain/kotlin/Cognition.kt`:

| Concept | Type | Meaning |
|---|---|---|
| **Cognition** | graph node | A reasoning session/conversation. Has `initiationMoment`, optional `title`/`summary`, may have a parent (`hasChild`). |
| **PhenomenalExpression** | `PhenomenalExpression` | One "turn" produced by an agent; has `initiationMoment`/`culminationMoment` and a list of phenomena. |
| **Phenomenon** | sealed `Phenomenon` | The atoms of perception/production: `Text`, `Image`, `Document`, `Intent`, `Fulfillment`. |
| **Intent** | `Phenomenon.Intent` | The agent's will to act, carrying `systemId`, `purpose`, and executable `code` (GolemScript). |
| **Fulfillment** | `Phenomenon.Fulfillment` | The result of executing an Intent (`result`, `impeded`); links back via `fulfills`. |
| **EpistemicAgent** | sealed | Who produced an expression: `AI(model, vendor)`, `Human`, `Computer`. |

The implicit memory graph (from the constitution) is:

```cypher
(parent:Cognition)-[:hasChild]->(cognition:Cognition)
(agent:EpistemicAgent)-[:creator]->(expression:PhenomenalExpression)
(cognition:Cognition)-[:hasPart]->(expression:PhenomenalExpression)
(expression:PhenomenalExpression)-[:hasPart]->(phenomenon:Phenomenon)
(fulfillment:Phenomenon:Fulfillment)-[:fulfills]->(intent:Phenomenon:Intent)
(fulfillment:Phenomenon:Fulfillment)-[:actualizes]->(fact:Any)
```

### 1.3 The cognitive (harness) loop

Implemented by `GolemXiv` in `golem-xiv-core/src/main/kotlin/GolemXiv.kt`:

1. `initiateCognition()` — create a `Cognition` seeded with the **constitution**
   (system prompt + environmental context).
2. `perceive(cognitionId, phenomena)` — append human/external phenomena, then
   spawn a coroutine running the loop:
   - `cognizer.reason(...)` streams `CognitionEvent`s from the LLM;
   - if the expression *culminated with an Intent*
     (`repository.maybeCulminatedWithIntent`), the `code` is run by
     `GolemScriptExecutor`;
   - the `Fulfillment` (or `impediment`) is persisted and broadcast;
   - **loop** while the latest expression keeps producing intents.
3. All events are streamed to clients through a `FlowCollector<GolemOutput>`
   (SSE in the server).

This is the "agent loop": *reason → emit intent → execute code → feed result
back → repeat until no intent*. It mirrors the standalone reference loop in
`golem-xiv-mini/.../GolemXivMini.kt`.

### 1.4 GolemScript & the Golem Markup Language

- **GolemScript** = Kotlin script compiled/executed by `GolemScriptExecutor`
  (`golem-xiv-core/.../script/GolemScriptExecution.kt`), with a **filesystem
  persistent across executions** and a `CoroutineScope` (so `suspend`
  functions and parallel `async` calls work directly).
- **Golem Markup Language** — `golem:`-prefixed XML tags carried in/out of the
  model stream. The `mini` constitution documents the full vocabulary:
  - `<golem:actant type="human|self|computer"/>` — provenance marker (the
    harness injects it; the model must never emit it).
  - `<golem:script>…</golem:script>` — code to execute (the closing tag is
    also a **stop sequence**).
  - `<golem:result>…</golem:result>` — execution output fed back.
  - `<golem:impediment>…</golem:impediment>` — compile/eval errors fed back.
- The full system instead extracts intents via `GolemScriptExtraction.kt` and
  the Anthropic tool-use channel (`IntentCognizer`), so there are **two
  surface dialects**: the markup-only `mini` path and the tool-use path in the
  full server (see §2.4 — Intent/`systemId` come from a single `GolemScript`
  tool).

### 1.5 Capabilities exposed to GolemScript

The **live** surface is whatever `GolemScriptDependencyProvider`
(`golem-xiv-core/.../script/GolemScriptDependencyProvider.kt`) injects:

| Binding | Backed by | Capability |
|---|---|---|
| `mind` | `ActualMind` | Retrospection: `currentCognition()`, `getCognition(id)`, title/summary, iterate past `expressions()`. |
| `memory` | Neo4j `Memory` | `remember { node{…}; relationship{…} }` transactional writes (schema.org vocab, provenance `source`/`confidence`) + `query(cypher)`. |
| `files` | `LocalFiles` | list/read/readBinary/create/exists/delete on a persistent FS. |
| `http` | `KtorHttp` | `get(url, accept)`, defaulting to Markdown (`r.jina.ai` / `markdown.law` idioms). |
| `kotlinMetadata` | `DefaultKotlinMetadata` | Reflection/metadata so the model can introspect available script APIs. |

Plus a domain helper baked into the constitution: **`markdown.law`** legal-document
retrieval (German federal law live; EU "coming soon").

> **Not wired in (exist as code only):** a `Shell`/bash service
> (`script/service/Shell.kt`) and Playwright web-browsing
> (`golem-xiv-playwright`, `script/incubation/WebBrowser.kt`) are present but
> **not** in the injected dependency list — so "Unix omnipotence" and headless
> browsing are not yet reachable by the running agent. `Secrets` and an MCP
> bridge sit under `script/incubation/` as well.

### 1.6 Cognizer (LLM-provider) abstraction

`golem-xiv-api-backend/.../Cognizer.kt`:

```kotlin
interface Cognizer {
    fun reason(
        constitution: List<String>,
        cognitionId: Long,
        phenomenalFlow: List<PhenomenalExpression>,
        hints: Map<String, String>
    ): Flow<CognitionEvent>
}
```

The contract is provider-agnostic and streaming. This is the seam meant to
deliver "LLM-independence". Two implementations exist:
`AnthropicToolUseCognizer` (full) and `DashscopeToolUseCognizer` (stub,
disabled in `settings.gradle.kts`).

### 1.7 Memory: current model vs. the GEEP-0001 target

- **Current:** episodic graph persistence (cognition/expression/phenomenon),
  plus *agent-driven* fact memorization (`memory.remember`) and *agent-driven*
  recall (`memory.query`, `mind.getCognition`). Memory operations happen
  **inside** the conscious loop and only when the model chooses.
- **Target (GEEP-0001, Draft):** make memory management a **separate,
  asynchronous "subconscious" process** — dual representation (embeddings for
  "what is this about" + graph for "how does it relate"), episodic→semantic
  **consolidation**, automatic retrieval on each prompt, conflict handling,
  decay/forgetting, periodic reorganization. **None of this is implemented**;
  `CognitiveProcessor` (the obvious home for it) is an empty class.

### 1.8 System surfaces

- `golem-xiv-server` — Ktor server, SSE broadcasting, optional cookie/password
  gate, Neo4j config, health endpoint.
- `golem-xiv-web` / `golem-xiv-presenter` / `golem-xiv-dom-export` — Kotlin/JS
  conversation UI (multiplatform presenter + DOM semantic-event export).
- `golem-xiv-cli` — thin CLI entry point.
- `golem-xiv-mini` — a ~340-line **self-contained reference implementation** of
  the loop (the cleanest expression of the intended core).
- `golem-xiv-neo4j-starter` — embedded Neo4j launcher (`runNeo4j`).

---

## Part 2 — Completeness estimate

### 2.1 Headline

**Overall: ~45–55% of the envisioned system; a working alpha of the *core
loop*, with most of the "moat" features (autonomous memory, multi-LLM,
omnipotence, auto-science, self-modification) still aspirational.**

The **inner loop works**: a single cognition can reason with Claude, emit
Kotlin-script intents, execute them, persist phenomena to Neo4j, and stream
everything to a web UI. What is largely missing is everything that makes the
README's bullet points *autonomous* and *provider-independent*.

### 2.2 Feature scorecard (vs. the marketed pillars)

| README pillar | State | Evidence |
|---|---|---|
| Chain-of-code (GolemScript) | ✅ ~90% | `GolemScriptExecutor` + extensive tests; persistent FS; coroutine scope. |
| Cognition / harness loop | ✅ ~85% | `GolemXiv.perceive` loop end-to-end; interruption is rudimentary. |
| Graph memory (knowledge graph) | 🟡 ~60% | Persistence + `remember`/`query` work; no embeddings, no auto-retrieval, no consolidation. |
| Metacognition / retrospection | 🟡 ~50% | `mind` retrospection exists; no "thinking about own thinking" loop, no `CognitiveProcessor`. |
| LLM-independence | 🟡 ~35% | Clean `Cognizer` seam, but only Anthropic works; Dashscope is a stub; OpenAI/Gemini/etc. are issues only (#31). |
| Mass parallelism | 🟡 ~30% | Parallel HTTP inside a script + multiple cognitions tracked; recursive child `cogitate` is **only in `mini`**, not wired in the full server. |
| Unix-omnipotence | 🔴 ~15% | `Shell` service exists but is **not injected** into GolemScript. |
| Self-modification | 🔴 ~10% | The agent runs code, but cannot edit its own constitution/code; `setConstitution` is commented out; `FUTURE.md` `SystemPromptChanger`/`RecursiveAgent` are `TODO`. |
| Auto-science (hypothesize/verify) | 🔴 ~5% | No dedicated subsystem; only emergent via free-form GolemScript. |
| Private org context | 🟡 ~40% | Self-hosted Neo4j + password gate; no multi-tenant/org isolation model; OAuth is issue #24. |

### 2.3 Module completeness

| Module | Completeness | Notes |
|---|---|---|
| `golem-xiv-api` | ✅ ~90% | Stable domain model; a couple of `TODO`s (Computer↔Human link). |
| `golem-xiv-api-backend` | 🟡 ~70% | Interfaces defined; some (`Memory`, secrets/MCP) partly conceptual. |
| `golem-xiv-core` | 🟡 ~70% | Loop + script engine solid; `CognitiveProcessor` empty; `DefaultCognitionRepository` has `TODO("Not yet implemented")` (e.g. `updateSystemPhenomena`, culmination moment stubbed); `LlmTextEditor` is 12 empty functions. |
| `golem-xiv-cognizer-anthropic` | ✅ ~90% | Production-quality streaming tool-use + ephemeral cache. |
| `golem-xiv-cognizer-dashscope` | 🔴 ~10% | Disabled; no tool-use; debug prints; hard-coded key. |
| `golem-xiv-neo4j` | 🟡 ~70% | CRUD + memory writes work; no migrations (#32), no embeddings; confidence partly unused. |
| `golem-xiv-server` | 🟡 ~70% | Loop wired + SSE + auth gate; thin on tests. |
| `golem-xiv-web`/`presenter`/`dom-export` | 🟡 ~60% | Functional UI; migration to `xemantic-kotlin-js` pending (#54). |
| `golem-xiv-json` | ✅ ~85% | Streaming JSON parser, well tested. |
| `golem-xiv-kotlin-metadata` | ✅ ~80% | Reflection support, 9 test files. |
| `golem-xiv-playwright` | 🔴 ~20% | Wrapper exists, not wired; `WebBrowsing` has `TODO`. |
| `golem-xiv-cli` / `logging` / `neo4j-starter` | 🟡 | Small glue modules; functional. |
| `golem-xiv-mini` | ✅ | Complete reference of the core loop (no tests). |

### 2.4 Test coverage

Real tests exist in only **7 of 18** modules (~25 test files):

- **Well covered:** `core` (GolemScript exec/extraction — the riskiest code),
  `neo4j`, `json`, `kotlin-metadata`.
- **Thin:** `server` (2), `presenter` (2), `dom-export` (2).
- **No tests at all:** `api`, `api-backend`, `api-client`,
  **both cognizers**, `cli`, `playwright`, `web`, `logging`, `neo4j-starter`,
  `mini`.

Notably the **cognizers have no tests** despite being the LLM-integration
heart, and there are **no end-to-end / integration tests** of the full
`perceive` loop. Neo4j *integration* testing is an open issue (#41). If the
task's premise is "a test suite exists that this should satisfy", current
coverage satisfies roughly the **script-execution and persistence** slices and
essentially **none of the agent-behaviour / provider / E2E** slices.

### 2.5 What the roadmap confirms is still open (upstream issues)

Open issues corroborate the gaps above and read as the remaining spec backlog:
multi-LLM (#31 OpenAI), Neo4j schema migrations (#32), Neo4j integration tests
(#41), DuckDuckGo search module (#48), WebSearch/`Web.fetch` via Playwright
(#29, #30), thinking tokens (#28), better ephemeral caching (#27), UI migration
(#54), release packaging (#40), reverse proxy + OAuth (#25, #24).

### 2.6 Most material incompleteness (ranked)

1. **Autonomous memory (GEEP-0001)** — the system's central differentiator;
   currently only manual, in-loop memory. `CognitiveProcessor` is empty.
2. **LLM-independence** — only Anthropic is real; the abstraction is ready but
   unproven against a second provider.
3. **Recursive/parallel cogitation in production** — proven in `mini`, not
   wired into the server path.
4. **Agent "omnipotence" surface** — shell + browsing exist but are
   un-injected; the agent's real reach today is `http`/`files`/`memory`/`mind`.
5. **`DefaultCognitionRepository` stubs** — `updateSystemPhenomena` throws;
   culmination timestamps not persisted — latent correctness gaps.
6. **Self-modification & auto-science** — essentially unstarted beyond running
   arbitrary code.
7. **Testing of behaviour** — no cognizer or E2E tests; no Neo4j integration
   tests yet.

### 2.7 Bottom line

Golem XIV is a **credible, well-architected alpha of its core idea** — an
LLM that acts exclusively by writing and running Kotlin against a graph memory.
The skeleton (ontology, loop, script engine, Anthropic provider, Neo4j
persistence, streaming UI) is in place and the riskiest piece (script
execution) is the best-tested. The **gap to the advertised product** is large
and concentrated in the autonomy/intelligence layer: subconscious memory
management, provider portability, productionized recursion, a broadened (and
safely-sandboxed) action surface, and behavioural test coverage. A reasonable
characterization is **"core loop ~80% done, full vision ~half done."**
