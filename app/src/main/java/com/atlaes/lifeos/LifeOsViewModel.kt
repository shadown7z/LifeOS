package com.atlaes.lifeos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atlaes.lifeos.data.HabitWithRecord
import com.atlaes.lifeos.data.LifeOsRepository
import com.atlaes.lifeos.data.database.JournalEntryEntity
import com.atlaes.lifeos.data.database.NoteEntity
import com.atlaes.lifeos.data.database.GoalEntity
import com.atlaes.lifeos.data.database.TagEntity
import com.atlaes.lifeos.data.database.NoteTagRelation
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
    val tags: List<TagEntity> = emptyList(),
    val noteTags: List<NoteTagRelation> = emptyList(),
    val journalEntries: List<JournalEntryEntity> = emptyList(),
    val isReady: Boolean = false,
)

class LifeOsViewModel(private val repository: LifeOsRepository) : ViewModel() {
    private val today = LocalDate.now()
    private val habits = repository.observeHabits(today)
    private val goals = repository.observeGoals()
    private val notes = repository.observeNotes()
    private val tags = repository.observeTags()
    private val noteTags = repository.observeNoteTags()
    private val journalEntries = repository.observeJournalEntries()

    val uiState: StateFlow<LifeOsUiState> = combine(habits, goals, notes, tags, noteTags, journalEntries) { values ->
        @Suppress("UNCHECKED_CAST")
        LifeOsUiState(habits = values[0] as List<HabitWithRecord>, goals = values[1] as List<GoalEntity>, notes = values[2] as List<NoteEntity>, tags = values[3] as List<TagEntity>, noteTags = values[4] as List<NoteTagRelation>, journalEntries = values[5] as List<JournalEntryEntity>, isReady = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LifeOsUiState())

    init {
        viewModelScope.launch { repository.ensureSeedData() }
    }

    fun setHabitCompleted(item: HabitWithRecord, completed: Boolean) {
        viewModelScope.launch { repository.setHabitCompleted(item.habit, today, completed) }
    }

    fun saveNote(id: String?, title: String, body: String, category: String, tags: List<String>) {
        viewModelScope.launch { repository.saveNote(id, title, body, category, tags) }
    }

    fun setNoteFavorite(note: NoteEntity, favorite: Boolean) { viewModelScope.launch { repository.setNoteFavorite(note, favorite) } }
    fun setNoteArchived(note: NoteEntity, archived: Boolean) { viewModelScope.launch { repository.setNoteArchived(note, archived) } }
    fun deleteTag(tag: TagEntity) { viewModelScope.launch { repository.deleteTag(tag) } }

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

    fun saveJournal(completed: String, learned: String, reflection: String, change: String, tomorrow: String, affirmation: String, gratitude: String, tomorrowThree: String) {
        viewModelScope.launch { repository.saveJournal(today, completed, learned, reflection, tomorrow, change, affirmation, gratitude, tomorrowThree) }
    }
}

class LifeOsViewModelFactory(private val repository: LifeOsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LifeOsViewModel::class.java))
        return LifeOsViewModel(repository) as T
    }
}
