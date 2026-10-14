package com.atlaes.lifeos.data

import androidx.room.withTransaction
import com.atlaes.lifeos.data.database.GoalEntity
import com.atlaes.lifeos.data.database.HabitEntity
import com.atlaes.lifeos.data.database.HabitRecordEntity
import com.atlaes.lifeos.data.database.JournalEntryEntity
import com.atlaes.lifeos.data.database.LifeOsDatabase
import com.atlaes.lifeos.data.database.NoteEntity
import com.atlaes.lifeos.data.database.NoteTagRelation
import com.atlaes.lifeos.data.database.TagEntity
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BackupCounts(
    val habits: Int,
    val habitRecords: Int,
    val goals: Int,
    val notes: Int,
    val tags: Int,
    val journalEntries: Int,
)

data class ImportPreview(
    val counts: BackupCounts,
    val formatVersion: Int,
    val appName: String,
)

class LifeOsBackupService(private val database: LifeOsDatabase) {
    fun noteMarkdown(note: NoteEntity): String = buildString {
        appendLine("---")
        appendLine("id: ${note.id}")
        appendLine("title: ${note.title.replace("\n", " ")}")
        appendLine("category: ${note.category}")
        appendLine("createdAt: ${Instant.ofEpochMilli(note.createdAt)}")
        appendLine("updatedAt: ${Instant.ofEpochMilli(note.updatedAt)}")
        appendLine("---")
        appendLine()
        appendLine("# ${note.title}")
        appendLine()
        append(note.body)
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val snapshot = readSnapshot()
        JSONObject().apply {
            put("formatVersion", 1)
            put("appName", "LifeOS")
            put("exportedAt", Instant.now().toString())
            put("entities", JSONObject().apply {
                put("habits", snapshot.habits.toJson { it.toJson() })
                put("habitRecords", snapshot.habitRecords.toJson { it.toJson() })
                put("goals", snapshot.goals.toJson { it.toJson() })
                put("notes", snapshot.notes.toJson { it.toJson() })
                put("tags", snapshot.tags.toJson { it.toJson() })
                put("noteTagRelations", snapshot.relations.toJson { it.toJson() })
                put("journalEntries", snapshot.journalEntries.toJson { it.toJson() })
            })
            put("preferences", JSONObject())
        }.toString(2)
    }

    fun preview(json: String): ImportPreview {
        val root = JSONObject(json)
        val version = root.optInt("formatVersion", -1)
        require(version == 1) { "不支持的数据格式版本：$version" }
        require(root.optString("appName") == "LifeOS") { "这不是 LifeOS 数据文件" }
        val entities = root.optJSONObject("entities") ?: error("缺少 entities 字段")
        return ImportPreview(
            counts = BackupCounts(
                habits = entities.arraySize("habits"),
                habitRecords = entities.arraySize("habitRecords"),
                goals = entities.arraySize("goals"),
                notes = entities.arraySize("notes"),
                tags = entities.arraySize("tags"),
                journalEntries = entities.arraySize("journalEntries"),
            ),
            formatVersion = version,
            appName = root.getString("appName"),
        )
    }

    suspend fun restore(json: String, replaceExisting: Boolean): ImportPreview = withContext(Dispatchers.IO) {
        val snapshot = parseSnapshot(json)
        database.withTransaction {
            if (replaceExisting) {
                database.clearAllTables()
                database.habitDao().upsertAll(snapshot.habits)
                snapshot.habitRecords.forEach { database.habitDao().upsertRecord(it) }
                snapshot.goals.forEach { database.goalDao().upsert(it) }
                snapshot.notes.forEach { database.noteDao().upsert(it) }
                snapshot.journalEntries.forEach { database.journalDao().upsert(it) }
                database.tagDao().insertAll(snapshot.tags)
                database.tagDao().insertRelations(snapshot.relations)
            } else {
                snapshot.habits.forEach { if (database.habitDao().findById(it.id) == null) database.habitDao().upsert(it) }
                snapshot.habitRecords.forEach { if (database.habitDao().findRecordById(it.id) == null) database.habitDao().upsertRecord(it) }
                snapshot.goals.forEach { if (database.goalDao().findById(it.id) == null) database.goalDao().upsert(it) }
                snapshot.notes.forEach { if (database.noteDao().findById(it.id) == null) database.noteDao().upsert(it) }
                snapshot.journalEntries.forEach { if (database.journalDao().findByDate(it.localDate) == null) database.journalDao().upsert(it) }
                val tagIdMap = mutableMapOf<String, String>()
                snapshot.tags.forEach { imported ->
                    val existing = database.tagDao().findById(imported.id) ?: database.tagDao().findByName(imported.name)
                    if (existing == null) database.tagDao().insertAll(listOf(imported))
                    tagIdMap[imported.id] = existing?.id ?: imported.id
                }
                snapshot.relations.forEach { relation ->
                    database.tagDao().insertRelations(listOf(relation.copy(tagId = tagIdMap[relation.tagId] ?: relation.tagId)))
                }
            }
        }
        preview(json)
    }

    private suspend fun readSnapshot() = BackupSnapshot(
        habits = database.habitDao().all(),
        habitRecords = database.habitDao().allRecords(),
        goals = database.goalDao().all(),
        notes = database.noteDao().all(),
        tags = database.tagDao().all(),
        relations = database.tagDao().allRelations(),
        journalEntries = database.journalDao().all(),
    )

    private fun parseSnapshot(json: String): BackupSnapshot {
        val root = JSONObject(json)
        val preview = preview(json)
        val entities = root.getJSONObject("entities")
        return BackupSnapshot(
            habits = entities.getJSONArray("habits").mapJson { it.toHabit() },
            habitRecords = entities.getJSONArray("habitRecords").mapJson { it.toHabitRecord() },
            goals = entities.getJSONArray("goals").mapJson { it.toGoal() },
            notes = entities.getJSONArray("notes").mapJson { it.toNote() },
            tags = entities.getJSONArray("tags").mapJson { it.toTag() },
            relations = entities.getJSONArray("noteTagRelations").mapJson { it.toRelation() },
            journalEntries = entities.getJSONArray("journalEntries").mapJson { it.toJournal() },
        ).also { require(preview.counts.habits == it.habits.size) }
    }
}

private data class BackupSnapshot(
    val habits: List<HabitEntity>,
    val habitRecords: List<HabitRecordEntity>,
    val goals: List<GoalEntity>,
    val notes: List<NoteEntity>,
    val tags: List<TagEntity>,
    val relations: List<NoteTagRelation>,
    val journalEntries: List<JournalEntryEntity>,
)

private fun <T> List<T>.toJson(mapper: (T) -> JSONObject): JSONArray = JSONArray().also { array -> forEach { array.put(mapper(it)) } }

private fun JSONObject.arraySize(name: String): Int = optJSONArray(name)?.length() ?: error("缺少数组字段：$name")

private inline fun <T> JSONArray.mapJson(mapper: (JSONObject) -> T): List<T> = buildList {
    for (index in 0 until length()) add(mapper(getJSONObject(index)))
}

private fun HabitEntity.toJson() = JSONObject().apply {
    put("id", id); put("title", title); put("targetValue", targetValue); put("unit", unit)
    put("isActive", isActive); put("createdAt", createdAt); put("updatedAt", updatedAt)
}

private fun HabitRecordEntity.toJson() = JSONObject().apply {
    put("id", id); put("habitId", habitId); put("localDate", localDate); put("value", value)
    put("completedAt", completedAt); put("note", note)
}

private fun GoalEntity.toJson() = JSONObject().apply {
    put("id", id); put("title", title); put("description", description); put("category", category)
    put("priority", priority); put("status", status); put("nextAction", nextAction)
    put("createdAt", createdAt); put("updatedAt", updatedAt); put("dueDate", dueDate); put("completedAt", completedAt)
}

private fun NoteEntity.toJson() = JSONObject().apply {
    put("id", id); put("title", title); put("body", body); put("category", category)
    put("isFavorite", isFavorite); put("isArchived", isArchived); put("createdAt", createdAt); put("updatedAt", updatedAt)
}

private fun TagEntity.toJson() = JSONObject().apply { put("id", id); put("name", name); put("createdAt", createdAt) }
private fun NoteTagRelation.toJson() = JSONObject().apply { put("noteId", noteId); put("tagId", tagId) }
private fun JournalEntryEntity.toJson() = JSONObject().apply {
    put("id", id); put("localDate", localDate); put("completedText", completedText); put("learnedText", learnedText)
    put("reflectionText", reflectionText); put("tomorrowText", tomorrowText); put("changeText", changeText)
    put("selfAffirmationText", selfAffirmationText); put("gratitudeText", gratitudeText); put("tomorrowThreeText", tomorrowThreeText)
    put("createdAt", createdAt); put("updatedAt", updatedAt)
}

private fun JSONObject.toHabit() = HabitEntity(getString("id"), getString("title"), getDouble("targetValue"), getString("unit"), getBoolean("isActive"), getLong("createdAt"), getLong("updatedAt"))
private fun JSONObject.toHabitRecord() = HabitRecordEntity(getString("id"), getString("habitId"), getString("localDate"), getDouble("value"), getLong("completedAt"), optString("note"))
private fun JSONObject.toGoal() = GoalEntity(getString("id"), getString("title"), optString("description"), optString("category", "当前阶段"), getInt("priority"), optString("status", "进行中"), optString("nextAction"), getLong("createdAt"), getLong("updatedAt"), optNullableString("dueDate"), optNullableLong("completedAt"))
private fun JSONObject.toNote() = NoteEntity(getString("id"), getString("title"), getString("body"), optString("category", "普通笔记"), optBoolean("isFavorite"), optBoolean("isArchived"), getLong("createdAt"), getLong("updatedAt"))
private fun JSONObject.toTag() = TagEntity(getString("id"), getString("name"), getLong("createdAt"))
private fun JSONObject.toRelation() = NoteTagRelation(getString("noteId"), getString("tagId"))
private fun JSONObject.toJournal() = JournalEntryEntity(getString("id"), getString("localDate"), optString("completedText"), optString("learnedText"), optString("reflectionText"), optString("tomorrowText"), optString("changeText"), optString("selfAffirmationText"), optString("gratitudeText"), optString("tomorrowThreeText"), getLong("createdAt"), getLong("updatedAt"))

private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else optString(name).ifBlank { null }
private fun JSONObject.optNullableLong(name: String): Long? = if (isNull(name)) null else optLong(name)
