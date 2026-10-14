package com.atlaes.lifeos.data

import com.atlaes.lifeos.data.database.GoalDao
import com.atlaes.lifeos.data.database.GoalEntity
import com.atlaes.lifeos.data.database.HabitDao
import com.atlaes.lifeos.data.database.HabitEntity
import com.atlaes.lifeos.data.database.HabitRecordEntity
import com.atlaes.lifeos.data.database.JournalEntryEntity
import com.atlaes.lifeos.data.database.JournalDao
import com.atlaes.lifeos.data.database.NoteDao
import com.atlaes.lifeos.data.database.NoteEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class HabitWithRecord(
    val habit: HabitEntity,
    val record: HabitRecordEntity?,
)

class LifeOsRepository(
    private val habitDao: HabitDao,
    private val goalDao: GoalDao,
    private val noteDao: NoteDao,
    private val journalDao: JournalDao,
) {
    fun observeHabits(date: LocalDate): Flow<List<HabitWithRecord>> = combineHabitStreams(
        habitDao.observeActiveHabits(),
        habitDao.observeRecordsForDate(date.toString()),
    )

    fun observeGoals(): Flow<List<GoalEntity>> = goalDao.observeOpenGoals()

    fun observeNotes(): Flow<List<NoteEntity>> = noteDao.observeActiveNotes()

    fun observeJournalEntries(): Flow<List<JournalEntryEntity>> = journalDao.observeEntries()

    suspend fun ensureSeedData() {
        if (habitDao.count() == 0) {
            val now = System.currentTimeMillis()
            habitDao.upsertAll(
                listOf(
                    HabitEntity(UUID.randomUUID().toString(), "背英语单词", createdAt = now, updatedAt = now),
                    HabitEntity(UUID.randomUUID().toString(), "跑步 3–5 km", targetValue = 3.0, unit = "km", createdAt = now, updatedAt = now),
                    HabitEntity(UUID.randomUUID().toString(), "深度思考 30–60 分钟", targetValue = 30.0, unit = "分钟", createdAt = now, updatedAt = now),
                    HabitEntity(UUID.randomUUID().toString(), "写每日日记", createdAt = now, updatedAt = now),
                ),
            )
        }
        if (goalDao.count() == 0) {
            val now = System.currentTimeMillis()
            goalDao.upsert(
                GoalEntity(
                    id = UUID.randomUUID().toString(),
                    title = "完成 LifeOS 第一版",
                    description = "建立一套真正服务每天的生活系统",
                    category = "当前阶段",
                    priority = 1,
                    status = "进行中",
                    nextAction = "完成本地数据持久化",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun setHabitCompleted(habit: HabitEntity, date: LocalDate, completed: Boolean) {
        if (completed) {
            habitDao.upsertRecord(
                HabitRecordEntity(
                    id = "${habit.id}:$date",
                    habitId = habit.id,
                    localDate = date.toString(),
                    completedAt = System.currentTimeMillis(),
                ),
            )
        } else {
            habitDao.deleteRecord(habit.id, date.toString())
        }
    }

    suspend fun saveHabit(id: String?, title: String, targetValue: Double = 0.0, unit: String = "") {
        require(title.isNotBlank()) { "习惯名称不能为空" }
        val now = System.currentTimeMillis()
        val existing = id?.let { habitDao.findById(it) }
        habitDao.upsert(HabitEntity(existing?.id ?: id ?: UUID.randomUUID().toString(), title.trim(), targetValue, unit, existing?.isActive ?: true, existing?.createdAt ?: now, now))
    }

    suspend fun deactivateHabit(habit: HabitEntity) {
        habitDao.upsert(habit.copy(isActive = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveGoal(id: String?, title: String, nextAction: String, category: String = "当前阶段") {
        require(title.isNotBlank()) { "目标标题不能为空" }
        val now = System.currentTimeMillis()
        val existing = id?.let { goalDao.findById(it) }
        goalDao.upsert(GoalEntity(existing?.id ?: id ?: UUID.randomUUID().toString(), title.trim(), existing?.description.orEmpty(), category, existing?.priority ?: 2, existing?.status ?: "进行中", nextAction.trim(), existing?.createdAt ?: now, now, existing?.dueDate, existing?.completedAt))
    }

    suspend fun deleteGoal(goal: GoalEntity) = goalDao.delete(goal)

    suspend fun saveNote(id: String?, title: String, body: String, category: String = "普通笔记") {
        require(title.isNotBlank() || body.isNotBlank()) { "笔记标题或正文不能为空" }
        val now = System.currentTimeMillis()
        val existing = id?.let { noteDao.findById(it) }
        noteDao.upsert(
            NoteEntity(
                id = existing?.id ?: id ?: UUID.randomUUID().toString(),
                title = title.trim().ifBlank { "未命名笔记" },
                body = body,
                category = category,
                isFavorite = existing?.isFavorite ?: false,
                isArchived = existing?.isArchived ?: false,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun deleteNote(note: NoteEntity) = noteDao.delete(note)

    suspend fun saveJournal(
        date: LocalDate,
        completedText: String,
        learnedText: String,
        reflectionText: String,
        tomorrowText: String,
    ) {
        val now = System.currentTimeMillis()
        val existing = journalDao.findByDate(date.toString())
        journalDao.upsert(
            JournalEntryEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                localDate = date.toString(),
                completedText = completedText,
                learnedText = learnedText,
                reflectionText = reflectionText,
                tomorrowText = tomorrowText,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    private fun combineHabitStreams(
        habits: Flow<List<HabitEntity>>,
        records: Flow<List<HabitRecordEntity>>,
    ): Flow<List<HabitWithRecord>> = combine(habits, records) { definitions, dayRecords ->
        val recordsByHabit = dayRecords.associateBy { it.habitId }
        definitions.map { HabitWithRecord(it, recordsByHabit[it.id]) }
    }
}
