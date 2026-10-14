package com.atlaes.lifeos.data

import androidx.room.withTransaction
import com.atlaes.lifeos.data.database.JournalEntryEntity
import com.atlaes.lifeos.data.database.LifeOsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class JournalImportPreview(val entries: List<JournalEntryEntity>) {
    val count: Int get() = entries.size
}

class JournalTransferService(private val database: LifeOsDatabase) {
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val entries = database.journalDao().all()
        JSONObject().apply {
            put("formatVersion", 1)
            put("appName", "LifeOS")
            put("dataType", "dailyJournal")
            put("exportedAt", java.time.Instant.now().toString())
            put("entries", JSONArray().also { array -> entries.forEach { array.put(it.toJson()) } })
        }.toString(2)
    }

    fun preview(json: String): JournalImportPreview {
        require(json.toByteArray(Charsets.UTF_8).size <= 20 * 1024 * 1024) { "日记文件不能超过 20 MB" }
        val root = JSONObject(json)
        require(root.optString("appName") == "LifeOS") { "这不是 LifeOS 日记文件" }
        require(root.optString("dataType") == "dailyJournal") { "文件类型不是每日日记" }
        require(root.optInt("formatVersion", -1) == 1) { "不支持的日记格式版本" }
        val array = root.optJSONArray("entries") ?: error("缺少 entries 字段")
        require(array.length() <= 5000) { "单次最多导入 5000 篇日记" }
        val entries = buildList {
            for (index in 0 until array.length()) add(array.getJSONObject(index).toJournal())
        }
        require(entries.map { it.localDate }.distinct().size == entries.size) { "文件中存在重复日期的日记" }
        return JournalImportPreview(entries)
    }

    suspend fun import(entries: List<JournalEntryEntity>, replaceSameDate: Boolean): Int = withContext(Dispatchers.IO) {
        database.withTransaction {
            var changed = 0
            entries.forEach { entry ->
                val existing = database.journalDao().findByDate(entry.localDate)
                if (existing == null || replaceSameDate) {
                    database.journalDao().upsert(entry.copy(id = existing?.id ?: entry.id))
                    changed++
                }
            }
            changed
        }
    }
}

private fun JournalEntryEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("localDate", localDate)
    put("completedText", completedText)
    put("learnedText", learnedText)
    put("reflectionText", reflectionText)
    put("tomorrowText", tomorrowText)
    put("changeText", changeText)
    put("selfAffirmationText", selfAffirmationText)
    put("gratitudeText", gratitudeText)
    put("tomorrowThreeText", tomorrowThreeText)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun JSONObject.toJournal() = JournalEntryEntity(
    id = getString("id"),
    localDate = getString("localDate"),
    completedText = optString("completedText"),
    learnedText = optString("learnedText"),
    reflectionText = optString("reflectionText"),
    tomorrowText = optString("tomorrowText"),
    changeText = optString("changeText"),
    selfAffirmationText = optString("selfAffirmationText"),
    gratitudeText = optString("gratitudeText"),
    tomorrowThreeText = optString("tomorrowThreeText"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)
