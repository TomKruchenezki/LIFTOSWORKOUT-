package app.liftos.domain.backup

import app.liftos.domain.model.Exercise
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId

enum class BackupSource { LIFTOS, FLOOT }

sealed interface ParseResult {
    data class Success(
        val file: BackupFile,
        val source: BackupSource,
        val warnings: List<String>,
    ) : ParseResult

    data class Failure(val message: String) : ParseResult
}

object BackupCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        explicitNulls = false
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /**
     * Parses text of any supported backup format.
     * @param libraryExercises used by the Floot adapter to map foreign exercise ids to library entries.
     */
    fun parse(
        text: String,
        libraryExercises: List<Exercise> = emptyList(),
        zone: ZoneId = app.liftos.domain.logic.TimeUtils.DEFAULT_ZONE,
        nowMs: Long = System.currentTimeMillis(),
    ): ParseResult {
        val root = try {
            json.parseToJsonElement(text.removePrefix("﻿"))
        } catch (e: Exception) {
            return ParseResult.Failure("הקובץ אינו JSON תקין.")
        }
        val obj = root as? JsonObject ?: return ParseResult.Failure("מבנה הקובץ אינו מוכר (נדרש אובייקט JSON).")

        val format = (obj["format"] as? JsonPrimitive)?.content
        return when {
            format == BackupFile.FORMAT -> parseNative(obj)
            format == null && FlootImporter.looksLikeFloot(obj) -> FlootImporter.convert(obj, libraryExercises, zone, nowMs)
            format != null -> ParseResult.Failure("פורמט לא מוכר: '$format'.")
            else -> ParseResult.Failure("הקובץ אינו גיבוי של LiftOS ואינו מזוהה כייצוא מהאפליקציה הקודמת.")
        }
    }

    private fun parseNative(obj: JsonObject): ParseResult {
        val version = try {
            obj["schemaVersion"]?.jsonPrimitive?.int ?: 1
        } catch (_: Exception) {
            return ParseResult.Failure("שדה schemaVersion אינו תקין.")
        }
        if (version > BackupFile.CURRENT_SCHEMA) {
            return ParseResult.Failure(
                "הגיבוי נוצר בגרסה חדשה יותר של האפליקציה (schema $version). עדכן את LiftOS ונסה שוב.",
            )
        }
        if (version < 1) return ParseResult.Failure("גרסת הגיבוי אינה תקינה ($version).")
        val upgraded = BackupMigrator.upgrade(obj, version)
        return try {
            val file = json.decodeFromJsonElement(BackupFile.serializer(), upgraded)
            ParseResult.Success(file, BackupSource.LIFTOS, emptyList())
        } catch (e: SerializationException) {
            ParseResult.Failure("מבנה הגיבוי פגום או חסר שדות חובה: ${e.message?.lineSequence()?.firstOrNull().orEmpty()}")
        } catch (e: IllegalArgumentException) {
            ParseResult.Failure("מבנה הגיבוי פגום: ${e.message?.lineSequence()?.firstOrNull().orEmpty()}")
        }
    }
}

/**
 * Upgrades older backup documents to [BackupFile.CURRENT_SCHEMA] one version at a time.
 * Add a `when (from)` branch here whenever the format changes; never edit past steps.
 */
object BackupMigrator {
    fun upgrade(doc: JsonObject, from: Int): JsonObject {
        var current = doc
        var version = from
        while (version < BackupFile.CURRENT_SCHEMA) {
            current = when (version) {
                // 1 -> 2 goes here when schema 2 exists.
                else -> current
            }
            version++
        }
        return current
    }
}
