package com.atlaes.lifeos.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HabitEntity::class, HabitRecordEntity::class, GoalEntity::class, NoteEntity::class, TagEntity::class, NoteTagRelation::class, JournalEntryEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class LifeOsDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun goalDao(): GoalDao
    abstract fun noteDao(): NoteDao
    abstract fun journalDao(): JournalDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile private var instance: LifeOsDatabase? = null

        fun getInstance(context: Context): LifeOsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LifeOsDatabase::class.java,
                "lifeos.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS notes (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, category TEXT NOT NULL, isFavorite INTEGER NOT NULL, isArchived INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_updatedAt ON notes(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_category ON notes(category)")
                db.execSQL("CREATE TABLE IF NOT EXISTS tags (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS note_tag_relations (noteId TEXT NOT NULL, tagId TEXT NOT NULL, PRIMARY KEY(noteId, tagId), FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_tag_relations_tagId ON note_tag_relations(tagId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS journal_entries (id TEXT NOT NULL PRIMARY KEY, localDate TEXT NOT NULL, completedText TEXT NOT NULL, learnedText TEXT NOT NULL, reflectionText TEXT NOT NULL, tomorrowText TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_journal_entries_localDate ON journal_entries(localDate)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN changeText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN selfAffirmationText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN gratitudeText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN tomorrowThreeText TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
