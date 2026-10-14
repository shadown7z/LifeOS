package com.atlaes.lifeos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atlaes.lifeos.data.HabitWithRecord
import com.atlaes.lifeos.data.LifeOsRepository
import com.atlaes.lifeos.data.database.JournalEntryEntity
import com.atlaes.lifeos.data.database.NoteEntity
import com.atlaes.lifeos.data.database.GoalEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LifeOsUiState(
    val habits: List<HabitWithRecord> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val journalEntries: List<JournalEntryEntity> = emptyList(),
    val isReady: Boolean = false,
)

class LifeOsViewModel(private val repository: LifeOsRepository) : ViewModel() {
    private val today = LocalDate.now()
    private val habits = repository.observeHabits(today)
    private val goals = repository.observeGoals()
    private val notes = repository.observeNotes()
    private val journalEntries = repository.observeJournalEntries()

    val uiState: StateFlow<LifeOsUiState> = combine(habits, goals, notes, journalEntries) { habitItems, goalItems, noteItems, journalItems ->
        LifeOsUiState(habits = habitItems, goals = goalItems, notes = noteItems, journalEntries = journalItems, isReady = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LifeOsUiState())

    init {
        viewModelScope.launch { repository.ensureSeedData() }
    }

    fun setHabitCompleted(item: HabitWithRecord, completed: Boolean) {
        viewModelScope.launch { repository.setHabitCompleted(item.habit, today, completed) }
    }

    fun saveNote(id: String?, title: String, body: String) {
        viewModelScope.launch { repository.saveNote(id, title, body) }
    }

    fun saveHabit(id: String?, title: String, targetValue: Double = 0.0, unit: String = "") {
        viewModelScope.launch { repository.saveHabit(id, title, targetValue, unit) }
    }

    fun deactivateHabit(habit: com.atlaes.lifeos.data.database.HabitEntity) {
        viewModelScope.launch { repository.deactivateHabit(habit) }
    }

    fun saveGoal(id: String?, title: String, nextAction: String) {
        viewModelScope.launch { repository.saveGoal(id, title, nextAction) }
    }

    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch { repository.deleteGoal(goal) }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { repository.deleteNote(note) }
    }

    fun saveJournal(completed: String, learned: String, reflection: String, tomorrow: String) {
        viewModelScope.launch { repository.saveJournal(today, completed, learned, reflection, tomorrow) }
    }
}

class LifeOsViewModelFactory(private val repository: LifeOsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LifeOsViewModel::class.java))
        return LifeOsViewModel(repository) as T
    }
}
