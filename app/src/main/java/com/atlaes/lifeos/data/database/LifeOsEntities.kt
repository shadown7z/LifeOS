package com.atlaes.lifeos.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val targetValue: Double = 0.0,
    val unit: String = "",
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "habit_records",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["habitId", "localDate"], unique = true),
        Index(value = ["localDate"]),
    ],
)
data class HabitRecordEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val localDate: String,
    val value: Double = 1.0,
    val completedAt: Long,
    val note: String = "",
)

@Entity(tableName = "goals", indices = [Index(value = ["status"]), Index(value = ["category"])])
data class GoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val category: String = "当前阶段",
    val priority: Int = 2,
    val status: String = "进行中",
    val nextAction: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val dueDate: String? = null,
    val completedAt: Long? = null,
)

@Entity(tableName = "notes", indices = [Index(value = ["updatedAt"]), Index(value = ["category"])])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val category: String = "普通笔记",
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "note_tag_relations",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["tagId"])],
)
data class NoteTagRelation(
    val noteId: String,
    val tagId: String,
)

@Entity(tableName = "journal_entries", indices = [Index(value = ["localDate"], unique = true)])
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val completedText: String = "",
    val learnedText: String = "",
    val reflectionText: String = "",
    val tomorrowText: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)
