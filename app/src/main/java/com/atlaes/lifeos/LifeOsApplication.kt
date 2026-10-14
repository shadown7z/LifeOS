package com.atlaes.lifeos

import android.app.Application
import com.atlaes.lifeos.data.LifeOsRepository
import com.atlaes.lifeos.data.ExternalBackupManager
import com.atlaes.lifeos.data.ThemePreference
import com.atlaes.lifeos.data.database.LifeOsDatabase

class LifeOsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        externalBackupManager.start()
    }

    val repository: LifeOsRepository by lazy {
        val database = LifeOsDatabase.getInstance(this)
        LifeOsRepository(database.habitDao(), database.goalDao(), database.noteDao(), database.journalDao(), database.tagDao(), database)
    }

    val database: LifeOsDatabase by lazy { LifeOsDatabase.getInstance(this) }
    val externalBackupManager: ExternalBackupManager by lazy { ExternalBackupManager(this, database) }
    val themePreference: ThemePreference by lazy { ThemePreference(this) }
}
