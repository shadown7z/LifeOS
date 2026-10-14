package com.atlaes.lifeos

import android.app.Application
import com.atlaes.lifeos.data.LifeOsRepository
import com.atlaes.lifeos.data.database.LifeOsDatabase

class LifeOsApplication : Application() {
    val repository: LifeOsRepository by lazy {
        val database = LifeOsDatabase.getInstance(this)
        LifeOsRepository(database.habitDao(), database.goalDao(), database.noteDao(), database.journalDao())
    }

    val database: LifeOsDatabase by lazy { LifeOsDatabase.getInstance(this) }
}
