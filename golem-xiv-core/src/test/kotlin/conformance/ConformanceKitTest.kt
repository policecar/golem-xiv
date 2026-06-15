/*
 * Golem XIV - Autonomous metacognitive AI system with semantic memory and self-directed research
 * Copyright (C) 2026  Kazimierz Pogoda / Xemantic
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xemantic.ai.golem.conformance

import com.xemantic.ai.golem.api.CognitionEvent
import com.xemantic.ai.golem.api.EpistemicAgent
import com.xemantic.ai.golem.api.GolemOutput
import com.xemantic.ai.golem.api.PhenomenalExpression
import com.xemantic.ai.golem.api.Phenomenon
import com.xemantic.ai.golem.api.backend.script.ExecuteGolemScript
import com.xemantic.ai.golem.api.backend.util.IntentCognizer
import com.xemantic.ai.golem.api.golemJson
import com.xemantic.ai.golem.core.script.GolemScriptExecutor
import com.xemantic.ai.golem.core.script.extractGolemScripts
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 1a — runs the language-neutral `golem-xiv-spec` conformance kit (vendored under
 * `src/test/resources/conformance/`) against the *real* Kotlin reference implementation.
 *
 * This is the de-risking step from `docs/PHASE-0-CONFORMANCE-KIT.md`: it turns the kit's
 * fixtures from "asserted internally consistent" into "verified against the reference
 * serializer / extractor / executor / intent decoder", so any future port (the Deno
 * harness) builds on trusted evidence.
 */
class ConformanceKitTest {

    private val fixture = Json { ignoreUnknownKeys = true }

    private fun resource(path: String): String =
        ConformanceKitTest::class.java.getResource(path)?.readText()
            ?: error("Missing conformance resource: $path")

    /** L1 — markup extraction (Dialect C): input stream -> extracted {purpose, code}. */
    @Test
    fun `markup-extraction corpus matches extractGolemScripts`() = runTest {
        val cases = fixture
            .parseToJsonElement(resource("/conformance/markup-extraction/corpus.json"))
            .jsonObject["cases"]!!.jsonArray

        for (case in cases) {
            val obj = case.jsonObject
            val name = obj["name"]!!.jsonPrimitive.content
            val input = obj["input"]!!.jsonPrimitive.content
            val expected = obj["expected"]!!.jsonArray.map {
                val e = it.jsonObject
                e["purpose"]!!.jsonPrimitive.content to e["code"]!!.jsonPrimitive.content
            }

            val whole = flowOf(input).extractGolemScripts().toList().map { it.purpose to it.code }
            assertEquals(expected, whole, "case '$name' (whole input)")

            // chunk-invariance: feeding the same bytes in 5-char chunks yields the same result
            if (input.isNotEmpty()) {
                val chunked = input.chunked(5).asFlow().extractGolemScripts().toList()
                    .map { it.purpose to it.code }
                assertEquals(expected, chunked, "case '$name' (chunked by 5)")
            }
        }
    }

    /** L1 — failure feedback: script error -> exact `<golem:impediment phase=...>` envelope. */
    @Test
    fun `impediment-envelope cases match executor reference output`() = runTest {
        val cases = fixture
            .parseToJsonElement(resource("/conformance/markup-extraction/impediment-envelope.json"))
            .jsonObject["cases"]!!.jsonArray

        val executor = GolemScriptExecutor()
        try {
            for (case in cases) {
                val obj = case.jsonObject
                val name = obj["name"]!!.jsonPrimitive.content
                val code = obj["code"]!!.jsonPrimitive.content
                val expectedMessage = obj["referenceMessage"]!!.jsonPrimitive.content

                val result = executor.execute(code)
                assertTrue(result is ExecuteGolemScript.Result.Error, "case '$name' expected an Error result")
                assertEquals(expectedMessage, result.message, "case '$name' impediment envelope")
            }
        } finally {
            executor.close()
        }
    }

    /** L0 — canonical serialization: every vector round-trips through the production [golemJson]. */
    @Test
    fun `serialization vectors round-trip through golemJson`() {
        val vectors = fixture
            .parseToJsonElement(resource("/conformance/serialization-vectors/vectors.json"))
            .jsonObject["vectors"]!!.jsonArray

        for (v in vectors) {
            val obj = v.jsonObject
            val name = obj["name"]!!.jsonPrimitive.content
            val schema = obj["schema"]!!.jsonPrimitive.content
            val original = obj["json"]!!
            val reencoded = roundTrip(schema, original)
            assertEquals(original, reencoded, "vector '$name' (schema $schema)")
        }
    }

    private fun roundTrip(schema: String, json: JsonElement): JsonElement = when {
        schema.endsWith("domain-model.schema.json#/\$defs/EpistemicAgent") ->
            golemJson.encodeToJsonElement(golemJson.decodeFromJsonElement<EpistemicAgent>(json))
        schema.endsWith("domain-model.schema.json#/\$defs/Phenomenon") ->
            golemJson.encodeToJsonElement(golemJson.decodeFromJsonElement<Phenomenon>(json))
        schema.endsWith("domain-model.schema.json") ->
            golemJson.encodeToJsonElement(golemJson.decodeFromJsonElement<PhenomenalExpression>(json))
        schema.endsWith("cognition-event.schema.json") ->
            golemJson.encodeToJsonElement(golemJson.decodeFromJsonElement<CognitionEvent>(json))
        schema.endsWith("golem-output.schema.json") ->
            golemJson.encodeToJsonElement(golemJson.decodeFromJsonElement<GolemOutput>(json))
        else -> error("Unmapped vector schema: $schema")
    }

    /**
     * L2 (decode core) — the tool-use transcript's `input_json` deltas, fed through the real
     * [IntentCognizer], produce exactly the transcript's Intent purpose/code sub-events.
     * (ExpressionInitiation/IntentInitiation/IntentCulmination/ExpressionCulmination are emitted
     * by the Cognizer wrapper around IntentCognizer and are covered by the full Phase-1 harness.)
     */
    @Test
    fun `tool-use transcript decodes through IntentCognizer`() {
        val doc = fixture
            .parseToJsonElement(resource("/conformance/replay-transcripts/tool-use-single-intent.transcript.json"))
            .jsonObject

        val stub = doc["stub"]!!.jsonObject
        val expressionId = stub["expressionId"]!!.jsonPrimitive.long
        val phenomenonId = stub["phenomenonIds"]!!.jsonArray[0].jsonPrimitive.long

        val deltas = doc["providerStream"]!!.jsonArray.mapNotNull { ev ->
            val o = ev.jsonObject
            if (o["event"]?.jsonPrimitive?.content != "content_block_delta") return@mapNotNull null
            val delta = o["delta"]!!.jsonObject
            if (delta["kind"]?.jsonPrimitive?.content != "input_json") return@mapNotNull null
            delta["partialJson"]!!.jsonPrimitive.content
        }

        val cognizer = IntentCognizer(expressionId, phenomenonId)
        val producedJson = deltas
            .flatMap { cognizer.add(it) }
            .map { golemJson.encodeToJsonElement<CognitionEvent>(it) }

        val expectedIntentSubEvents: List<JsonElement> = doc["expectedEvents"]!!.jsonArray
            .map { it.jsonObject }
            .filter {
                val t = it["type"]!!.jsonPrimitive.content
                t.startsWith("IntentPurpose") || t.startsWith("IntentCode")
            }

        assertEquals(expectedIntentSubEvents, producedJson)
    }

}
