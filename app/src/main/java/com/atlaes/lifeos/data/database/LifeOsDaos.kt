package com.atlaes.lifeos.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    suspend fun all(): List<HabitEntity>

    @Query("SELECT * FROM habit_records ORDER BY localDate ASC")
    suspend fun allRecords(): List<HabitRecordEntity>
    @Query("SELECT * FROM habits WHERE isActive = 1 ORDER BY createdAt ASC")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_records WHERE localDate = :localDate")
    fun observeRecordsForDate(localDate: String): Flow<List<HabitRecordEntity>>

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun count(): Int

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): HabitEntity?

    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Upsert
    suspend fun upsertAll(habits: List<HabitEntity>)

    @Upsert
    suspend fun upsertRecord(record: HabitRecordEntity)

    @Query("DELETE FROM habit_records WHERE habitId = :habitId AND localDate = :localDate")
    suspend fun deleteRecord(habitId: String, localDate: String)

    @Query("SELECT COUNT(*) FROM habit_records WHERE habitId = :habitId")
    suspend fun countRecordsForHabit(habitId: String): Int

    @Query("SELECT * FROM habit_records WHERE id = :id LIMIT 1")
    suspend fun findRecordById(id: String): HabitRecordEntity?

    @Delete
    suspend fun delete(habit: HabitEntity)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt ASC")
    suspend fun all(): List<GoalEntity>
    @Query("SELECT * FROM goals WHERE status IN ('待开始', '进行中') ORDER BY priority ASC, updatedAt DESC")
    fun observeOpenGoals(): Flow<List<GoalEntity>>

    @Query("SELECT COUNT(*) FROM goals")
    suspend fun count(): Int

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): GoalEntity?

    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Delete
    suspend fun delete(goal: GoalEntity)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt ASC")
    suspend fun all(): List<NoteEntity>
    @Query("SELECT * FROM notes ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM note_tag_relations")
    fun observeRelations(): Flow<List<NoteTagRelation>>

    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    suspend fun all(): List<TagEntity>

    @Query("SELECT * FROM note_tag_relations")
    suspend fun allRelations(): List<NoteTagRelation>

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Upsert
    suspend fun insertAll(tags: List<TagEntity>)

    @Upsert
    suspend fun insertRelations(relations: List<NoteTagRelation>)

    @Query("DELETE FROM note_tag_relations WHERE noteId = :noteId")
    suspend fun deleteRelationsForNote(noteId: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: String)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY localDate ASC")
    suspend fun all(): List<JournalEntryEntity>
    @Query("SELECT * FROM journal_entries ORDER BY localDate DESC")
    fun observeEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE localDate = :localDate LIMIT 1")
    suspend fun findByDate(localDate: String): JournalEntryEntity?

    @Upsert
    suspend fun upsert(entry: JournalEntryEntity)

    @Delete
    suspend fun delete(entry: JournalEntryEntity)
}
