package com.atlaes.lifeos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlaes.lifeos.data.database.HabitEntity
import com.atlaes.lifeos.data.database.HabitRecordEntity
import com.atlaes.lifeos.data.LifeOsBackupService
import com.atlaes.lifeos.data.database.NoteEntity
import com.atlaes.lifeos.data.database.LifeOsDatabase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifeOsDatabaseTest {
    private lateinit var database: LifeOsDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LifeOsDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun recordsForDifferentDatesRemainIndependent() = runBlocking {
        val habit = HabitEntity("habit-1", "背英语单词", createdAt = 1L, updatedAt = 1L)
        database.habitDao().upsertAll(listOf(habit))

        database.habitDao().upsertRecord(record(habit.id, LocalDate.of(2026, 10, 14)))
        database.habitDao().upsertRecord(record(habit.id, LocalDate.of(2026, 10, 15)))

        assertEquals(2, database.habitDao().countRecordsForHabit(habit.id))
    }

    @Test
    fun sameDateUpsertKeepsOneRecord() = runBlocking {
        val habit = HabitEntity("habit-2", "跑步", createdAt = 1L, updatedAt = 1L)
        database.habitDao().upsertAll(listOf(habit))
        val date = LocalDate.of(2026, 10, 14)

        database.habitDao().upsertRecord(record(habit.id, date, completedAt = 1L))
        database.habitDao().upsertRecord(record(habit.id, date, completedAt = 2L))

        assertEquals(1, database.habitDao().countRecordsForHabit(habit.id))
    }

    @Test
    fun jsonBackupKeepsChineseAndNewlines() = runBlocking {
        database.noteDao().upsert(
            NoteEntity(
                id = "note-1",
                title = "想法：长期主义",
                body = "第一行\n第二行，包含 \"引号\"",
                createdAt = 1L,
                updatedAt = 2L,
            ),
        )

        val service = LifeOsBackupService(database)
        val json = service.exportJson()
        val preview = service.preview(json)

        assertEquals(1, preview.counts.notes)
        database.clearAllTables()
        service.restore(json, replaceExisting = true)
        assertEquals("想法：长期主义", database.noteDao().all().single().title)
        assertEquals("第一行\n第二行，包含 \"引号\"", database.noteDao().all().single().body)
    }

    private fun record(habitId: String, date: LocalDate, completedAt: Long = 1L) = HabitRecordEntity(
        id = "$habitId:$date",
        habitId = habitId,
        localDate = date.toString(),
        completedAt = completedAt,
    )
}
