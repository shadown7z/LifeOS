package com.atlaes.lifeos.data

import androidx.room.withTransaction
import com.atlaes.lifeos.data.database.LifeOsDatabase
import com.atlaes.lifeos.data.database.NoteEntity
import com.atlaes.lifeos.data.database.NoteTagRelation
import com.atlaes.lifeos.data.database.TagEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MarkdownNote(val note: NoteEntity, val tags: List<String>)

class NoteMarkdownService(private val database: LifeOsDatabase) {
    fun markdown(item: MarkdownNote): String = buildString {
        appendLine("---")
        appendLine("id: ${item.note.id}")
        appendLine("title: ${item.note.title.replace('\n', ' ')}")
        appendLine("category: ${item.note.category.replace('\n', ' ')}")
        appendLine("tags: ${item.tags.joinToString(", ")}")
        appendLine("createdAt: ${Instant.ofEpochMilli(item.note.createdAt)}")
        appendLine("updatedAt: ${Instant.ofEpochMilli(item.note.updatedAt)}")
        appendLine("---")
        appendLine()
        appendLine("# ${item.note.title}")
        appendLine()
        append(item.note.body)
    }

    suspend fun allNotes(): List<MarkdownNote> = withContext(Dispatchers.IO) {
        val tags = database.tagDao().all().associateBy { it.id }
        val relations = database.tagDao().allRelations().groupBy { it.noteId }
        database.noteDao().all().map { note ->
            MarkdownNote(note, relations[note.id].orEmpty().mapNotNull { tags[it.tagId]?.name })
        }
    }

    fun zip(items: List<MarkdownNote>): ByteArray {
        require(items.size <= 500) { "最多导出 500 篇笔记" }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            items.forEachIndexed { index, item ->
                val name = item.note.title.replace(Regex("""[^\p{L}\p{N} _-]"""), "_").trim().take(60).ifBlank { "note" }
                zip.putNextEntry(ZipEntry("notes/${index + 1}-$name.md"))
                zip.write(markdown(item).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun parseMarkdown(text: String): MarkdownNote {
        require(text.toByteArray(Charsets.UTF_8).size <= 1024 * 1024) { "单篇笔记不能超过 1 MB" }
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n")
        val metadata = mutableMapOf<String, String>()
        val bodyStart = if (normalized.startsWith("---\n")) {
            val end = normalized.indexOf("\n---\n", 4)
            require(end in 4..8192) { "Markdown 元数据无效" }
            normalized.substring(4, end).lineSequence().forEach { line ->
                val divider = line.indexOf(':')
                if (divider > 0) metadata[line.substring(0, divider).trim()] = line.substring(divider + 1).trim()
            }
            end + 5
        } else 0
        val content = normalized.substring(bodyStart).trimStart('\n')
        val heading = content.lineSequence().firstOrNull()?.takeIf { it.startsWith("# ") }?.removePrefix("# ")?.trim()
        val title = metadata["title"]?.takeIf { it.isNotBlank() } ?: heading ?: "未命名笔记"
        val body = if (heading != null) content.substringAfter('\n', "").trimStart('\n') else content
        require(title.isNotBlank() || body.isNotBlank()) { "笔记内容为空" }
        val stableId = metadata["id"]?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
            ?: UUID.nameUUIDFromBytes("$title\n$body".toByteArray(Charsets.UTF_8)).toString()
        val now = System.currentTimeMillis()
        val createdAt = metadata["createdAt"]?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: now
        val updatedAt = metadata["updatedAt"]?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: createdAt
        return MarkdownNote(NoteEntity(stableId, title, body, metadata["category"]?.ifBlank { "普通笔记" } ?: "普通笔记", createdAt = createdAt, updatedAt = updatedAt), metadata["tags"].orEmpty().split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }.distinct())
    }

    fun parseZip(bytes: ByteArray): List<MarkdownNote> {
        require(bytes.size <= 20 * 1024 * 1024) { "ZIP 文件不能超过 20 MB" }
        val items = mutableListOf<MarkdownNote>()
        var totalBytes = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.name.startsWith('/') && !entry.name.contains("..") && !entry.name.contains('\\')) { "ZIP 包含无效路径" }
                if (!entry.isDirectory) {
                    require(entry.name.endsWith(".md", ignoreCase = true)) { "ZIP 中包含非 Markdown 文件" }
                    require(items.size < 500) { "ZIP 最多包含 500 篇笔记" }
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        require(totalBytes <= 20 * 1024 * 1024 && output.size() + read <= 1024 * 1024) { "ZIP 解压内容过大" }
                        output.write(buffer, 0, read)
                    }
                    items += parseMarkdown(output.toString(Charsets.UTF_8.name()))
                }
                zip.closeEntry()
            }
        }
        require(items.isNotEmpty()) { "ZIP 中没有 Markdown 笔记" }
        return items
    }

    suspend fun import(items: List<MarkdownNote>): Int = withContext(Dispatchers.IO) {
        database.withTransaction {
            var added = 0
            items.distinctBy { it.note.id }.forEach { item ->
                if (database.noteDao().findById(item.note.id) == null) {
                    database.noteDao().upsert(item.note)
                    item.tags.forEach { name ->
                        val tag = database.tagDao().findByName(name) ?: TagEntity(UUID.randomUUID().toString(), name, System.currentTimeMillis()).also { database.tagDao().insertAll(listOf(it)) }
                        database.tagDao().insertRelations(listOf(NoteTagRelation(item.note.id, tag.id)))
                    }
                    added++
                }
            }
            added
        }
    }
}
