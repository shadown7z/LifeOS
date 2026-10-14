package com.atlaes.lifeos

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import com.atlaes.lifeos.data.BackupCounts
import com.atlaes.lifeos.data.ImportPreview
import com.atlaes.lifeos.data.LifeOsBackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LifeOsTheme { LifeOsApp() } }
    }
}

private data class AppDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    AppDestination("dashboard", "今日", Icons.Default.Home),
    AppDestination("goals", "目标", Icons.Default.Flag),
    AppDestination("notes", "笔记", Icons.Default.Description),
    AppDestination("habits", "习惯", Icons.Default.TrackChanges),
    AppDestination("more", "更多", Icons.Default.MoreHoriz),
)

private fun topLevelRoute(route: String?): String = when (route) {
    "journal", "settings" -> "more"
    else -> route ?: "dashboard"
}

@Composable
fun LifeOsApp() {
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as LifeOsApplication
    val lifeOsViewModel: LifeOsViewModel = viewModel(factory = LifeOsViewModelFactory(application.repository))
    val uiState by lifeOsViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTopLevelRoute = topLevelRoute(backStackEntry?.destination?.route)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentTopLevelRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Bottom navigation is a top-level switch. Clear child routes and
                                // avoid restoring a stale saved entry from another top-level tab.
                                popUpTo("dashboard") { saveState = false }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding),
        ) {
            composable("dashboard") { DashboardScreen(state = uiState, onOpen = navController::navigate, onToggleHabit = lifeOsViewModel::setHabitCompleted) }
            composable("goals") { GoalsScreen(goals = uiState.goals, onSave = lifeOsViewModel::saveGoal, onDelete = lifeOsViewModel::deleteGoal) }
            composable("notes") { NotesScreen(notes = uiState.notes, onSave = lifeOsViewModel::saveNote, onDelete = lifeOsViewModel::deleteNote) }
            composable("habits") { HabitsScreen(habits = uiState.habits, onSave = lifeOsViewModel::saveHabit, onDeactivate = lifeOsViewModel::deactivateHabit) }
            composable("more") { MoreScreen(onOpen = navController::navigate) }
            composable("journal") { JournalScreen(entries = uiState.journalEntries, onSave = lifeOsViewModel::saveJournal) }
            composable("settings") { SettingsScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LifeOsPage(
    title: String,
    eyebrow: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(title, fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = { action?.invoke() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = { padding ->
            AnimatedContent(targetState = title, label = "page-transition") { targetTitle ->
                key(targetTitle) { content(padding) }
            }
        },
    )
}

@Composable
private fun DashboardScreen(
    state: LifeOsUiState,
    onOpen: (String) -> Unit,
    onToggleHabit: (com.atlaes.lifeos.data.HabitWithRecord, Boolean) -> Unit,
) {
    val habits = state.habits
    val completed = habits.count { it.record != null }
    val progress = if (habits.isEmpty()) 0f else completed.toFloat() / habits.size
    val dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE  M月d日"))
    LifeOsPage(title = "今天也向前一点", eyebrow = dateLabel, action = {
        IconButton(onClick = { onOpen("notes") }) { Icon(Icons.Default.Add, contentDescription = "快速新增") }
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("早上好，保持清醒", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("把注意力留给今天最重要的事", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                ProgressCard(progress = progress, completed = completed, total = habits.size)
            }
            item { SectionHeading("今日习惯", "全部习惯", onClick = { onOpen("habits") }) }
            items(habits) { item ->
                HabitRow(
                    title = item.habit.title,
                    done = item.record != null,
                    onToggle = { onToggleHabit(item, item.record == null) },
                )
            }
            item { SectionHeading("阶段重点", "查看目标", onClick = { onOpen("goals") }) }
            item { FocusGoalCard() }
            item { QuoteCard() }
        }
    }
}

@Composable
private fun ProgressCard(progress: Float, completed: Int, total: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("今日完成度", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Text("$completed / $total", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .7f))
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun HabitRow(title: String, done: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = done, onCheckedChange = { onToggle() })
            Text(title, modifier = Modifier.weight(1f), fontWeight = if (done) FontWeight.Medium else FontWeight.Normal, color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            AnimatedContent(targetState = done, label = "habit-status") { checked ->
                if (checked) Icon(Icons.Default.CheckCircle, contentDescription = "已完成", tint = MaterialTheme.colorScheme.primary)
                else Icon(Icons.Default.ChevronRight, contentDescription = "待完成", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun FocusGoalCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("完成 LifeOS 第一版", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text("进行中") })
            }
            Text("建立一套真正服务每天的生活系统", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = { .62f }, modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(12.dp))
                Text("62%", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun QuoteCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("今日箴言", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("“专注于能控制的事，时间会替你完成剩下的事。”", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun GoalsScreen(
    goals: List<com.atlaes.lifeos.data.database.GoalEntity>,
    onSave: (String?, String, String) -> Unit,
    onDelete: (com.atlaes.lifeos.data.database.GoalEntity) -> Unit,
) {
    var editorGoal by remember { mutableStateOf<com.atlaes.lifeos.data.database.GoalEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<com.atlaes.lifeos.data.database.GoalEntity?>(null) }
    var title by remember { mutableStateOf("") }
    var nextAction by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    fun openEditor(goal: com.atlaes.lifeos.data.database.GoalEntity?) {
        editorGoal = goal; title = goal?.title.orEmpty(); nextAction = goal?.nextAction.orEmpty(); showEditor = true
    }
    LifeOsPage(title = "目标", eyebrow = "清晰方向，持续推进", action = { IconButton(onClick = { openEditor(null) }) { Icon(Icons.Default.Add, contentDescription = "新增目标") } }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SearchInput("搜索目标", query) { query = it } }
            item { SectionHeading("当前阶段", "筛选", onClick = {}) }
            items(goals.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.nextAction.contains(query, ignoreCase = true) }) { goal -> GoalRow(goal.title, goal.nextAction, onClick = { openEditor(goal) }) }
            item { SectionHeading("以后再说", "查看全部", onClick = {}) }
            item { EmptyHint("把还没准备好的想法先放在这里") }
        }
    }
    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editorGoal == null) "新增目标" else "编辑目标") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("目标标题") }, singleLine = true)
                    OutlinedTextField(value = nextAction, onValueChange = { nextAction = it }, label = { Text("下一步行动") }, minLines = 2)
                }
            },
            dismissButton = { if (editorGoal != null) TextButton(onClick = { pendingDelete = editorGoal; showEditor = false }) { Text("删除") } },
            confirmButton = { Button(enabled = title.isNotBlank(), onClick = { onSave(editorGoal?.id, title, nextAction); showEditor = false }) { Text("保存") } },
        )
    }
    pendingDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这个目标？") },
            text = { Text("删除后无法在应用内恢复") },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
            confirmButton = { Button(onClick = { onDelete(goal); pendingDelete = null }) { Text("删除") } },
        )
    }
}

@Composable
private fun GoalRow(title: String, nextAction: String, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text("下一步：${nextAction.ifBlank { "拆出一个今天能完成的行动" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "查看详情", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun NotesScreen(
    notes: List<com.atlaes.lifeos.data.database.NoteEntity>,
    onSave: (String?, String, String) -> Unit,
    onDelete: (com.atlaes.lifeos.data.database.NoteEntity) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as LifeOsApplication
    val backupService = remember { LifeOsBackupService(app.database) }
    val scope = rememberCoroutineScope()
    var editorNote by remember { mutableStateOf<com.atlaes.lifeos.data.database.NoteEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<com.atlaes.lifeos.data.database.NoteEntity?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var noteToExport by remember { mutableStateOf<com.atlaes.lifeos.data.database.NoteEntity?>(null) }
    val markdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val note = noteToExport
        if (uri != null && note != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(backupService.noteMarkdown(note).toByteArray(Charsets.UTF_8)) }
                        ?: error("无法打开目标文件")
                }.onSuccess { withContext(Dispatchers.Main) { Toast.makeText(context, "Markdown 已导出", Toast.LENGTH_SHORT).show() } }
                    .onFailure { error -> withContext(Dispatchers.Main) { Toast.makeText(context, "导出失败：${error.message}", Toast.LENGTH_LONG).show() } }
            }
        }
        noteToExport = null
    }
    fun openEditor(note: com.atlaes.lifeos.data.database.NoteEntity?) {
        editorNote = note
        title = note?.title.orEmpty()
        body = note?.body.orEmpty()
        showEditor = true
    }
    LifeOsPage(title = "笔记", eyebrow = "把想法放在合适的位置", action = { IconButton(onClick = { openEditor(null) }) { Icon(Icons.Default.Add, contentDescription = "新增笔记") } }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SearchInput("搜索标题、正文或标签", query) { query = it } }
            item { SectionHeading("最近更新", "标签", onClick = {}) }
            val filteredNotes = notes.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true) }
            items(filteredNotes) { note -> NoteRow(note, onClick = { openEditor(note) }) }
            if (filteredNotes.isEmpty()) item { EmptyHint(if (query.isBlank()) "写下一个刚刚想到的念头" else "没有匹配的笔记") }
        }
    }
    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editorNote == null) "新增笔记" else "编辑笔记") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("正文") }, minLines = 5)
                }
            },
            dismissButton = {
                if (editorNote != null) TextButton(onClick = { pendingDelete = editorNote; showEditor = false }) { Text("删除") }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editorNote != null) TextButton(onClick = { noteToExport = editorNote; markdownLauncher.launch("${safeFileName(editorNote?.title ?: "note")}.md") }) { Text("导出 Markdown") }
                    Button(enabled = title.isNotBlank() || body.isNotBlank(), onClick = {
                        onSave(editorNote?.id, title, body)
                        showEditor = false
                    }) { Text("保存") }
                }
            },
        )
    }
    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这篇笔记？") },
            text = { Text("删除后无法在应用内恢复") },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
            confirmButton = { Button(onClick = { onDelete(note); pendingDelete = null }) { Text("删除") } },
        )
    }
}

@Composable
private fun NoteRow(note: com.atlaes.lifeos.data.database.NoteEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.BookmarkBorder, contentDescription = "收藏", tint = MaterialTheme.colorScheme.outline)
            }
            Text(note.body.ifBlank { "还没有正文" }, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(note.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HabitsScreen(
    habits: List<com.atlaes.lifeos.data.HabitWithRecord>,
    onSave: (String?, String, Double, String) -> Unit,
    onDeactivate: (com.atlaes.lifeos.data.database.HabitEntity) -> Unit,
) {
    var editorHabit by remember { mutableStateOf<com.atlaes.lifeos.data.database.HabitEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var targetValue by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    fun openEditor(habit: com.atlaes.lifeos.data.database.HabitEntity?) {
        editorHabit = habit; title = habit?.title.orEmpty(); targetValue = habit?.targetValue?.takeIf { it > 0 }?.toString().orEmpty(); unit = habit?.unit.orEmpty(); showEditor = true
    }
    LifeOsPage(title = "习惯", eyebrow = "稳定的小事会带来变化", action = { IconButton(onClick = { openEditor(null) }) { Icon(Icons.Default.Add, contentDescription = "新增习惯") } }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { HabitSummaryCard() }
            item { SectionHeading("每日习惯", "历史记录", onClick = {}) }
            items(habits) { item -> HabitDetailRow(item.habit.title, item.record != null, onClick = { openEditor(item.habit) }) }
        }
    }
    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editorHabit == null) "新增习惯" else "编辑习惯") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("习惯名称") }, singleLine = true)
                    OutlinedTextField(value = targetValue, onValueChange = { targetValue = it }, label = { Text("目标值 可选") }, singleLine = true)
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("单位 可选") }, singleLine = true)
                }
            },
            dismissButton = {
                if (editorHabit != null) TextButton(onClick = { onDeactivate(editorHabit!!); showEditor = false }) { Text("停用") }
            },
            confirmButton = {
                Button(enabled = title.isNotBlank(), onClick = { onSave(editorHabit?.id, title, targetValue.toDoubleOrNull() ?: 0.0, unit); showEditor = false }) { Text("保存") }
            },
        )
    }
}

@Composable
private fun HabitSummaryCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(22.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("本周坚持", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("18 次", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.Spa, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HabitDetailRow(title: String, completed: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(if (completed) "今天已完成" else "今天待完成", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(if (completed) "完成" else "今天", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MoreScreen(onOpen: (String) -> Unit) {
    LifeOsPage(title = "更多", eyebrow = "回顾与管理") { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MoreRow(Icons.Default.EditNote, "每日日记", "记录今天，给明天留下一点方向") { onOpen("journal") }
            MoreRow(Icons.Default.Settings, "设置", "主题、数据导入导出和备份恢复") { onOpen("settings") }
            Spacer(Modifier.height(12.dp))
            Text("LifeOS 1.0  ·  本地优先", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.ChevronRight, contentDescription = "打开", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun JournalScreen(
    entries: List<com.atlaes.lifeos.data.database.JournalEntryEntity>,
    onSave: (String, String, String, String) -> Unit,
) {
    val today = LocalDate.now().toString()
    val entry = entries.firstOrNull { it.localDate == today }
    var completed by remember(entry?.id) { mutableStateOf(entry?.completedText.orEmpty()) }
    var learned by remember(entry?.id) { mutableStateOf(entry?.learnedText.orEmpty()) }
    var reflection by remember(entry?.id) { mutableStateOf(entry?.reflectionText.orEmpty()) }
    var tomorrow by remember(entry?.id) { mutableStateOf(entry?.tomorrowText.orEmpty()) }
    val dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE  M月d日"))
    LifeOsPage(title = "每日日记", eyebrow = dateLabel, action = { TextButton(onClick = { onSave(completed, learned, reflection, tomorrow) }) { Text("保存") } }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("给今天留几句话", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item { JournalField("今天完成了什么？", completed) { completed = it } }
            item { JournalField("今天学会了什么？", learned) { learned = it } }
            item { JournalField("今天有什么值得反思的？", reflection) { reflection = it } }
            item { JournalField("明天最重要的一件事是什么？", tomorrow) { tomorrow = it } }
            item { Text("日记按日期保存，今天的内容可以随时继续编辑", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun JournalField(prompt: String, value: String, onValueChange: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(prompt) }, modifier = Modifier.fillMaxWidth().padding(8.dp), minLines = 3)
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as LifeOsApplication
    val service = remember { LifeOsBackupService(app.database) }
    val scope = rememberCoroutineScope()
    var importJson by remember { mutableStateOf<String?>(null) }
    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val json = service.exportJson()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("无法打开目标文件")
                }.onSuccess { withContext(Dispatchers.Main) { Toast.makeText(context, "LifeOS 数据已导出", Toast.LENGTH_SHORT).show() } }
                    .onFailure { error -> withContext(Dispatchers.Main) { Toast.makeText(context, "导出失败：${error.message}", Toast.LENGTH_LONG).show() } }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("无法读取文件")
                    service.preview(json) to json
                }.onSuccess { (preview, json) -> withContext(Dispatchers.Main) { importPreview = preview; importJson = json } }
                    .onFailure { error -> withContext(Dispatchers.Main) { Toast.makeText(context, "导入文件无效：${error.message}", Toast.LENGTH_LONG).show() } }
            }
        }
    }
    LifeOsPage(title = "设置", eyebrow = "让 LifeOS 更适合你") { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SettingGroupTitle("外观") }
            item { SettingRow(Icons.Default.WbSunny, "主题", "跟随系统") }
            item { SettingGroupTitle("数据") }
            item { SettingRow(Icons.Default.CheckCircle, "导入数据", "从 LifeOS JSON 导入并预览", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) }
            item { SettingRow(Icons.Default.BookmarkBorder, "导出数据", "保存完整 JSON 数据", onClick = { exportLauncher.launch("lifeos-backup.json") }) }
            item { SettingRow(Icons.Default.Spa, "备份与恢复", "导入页可选择合并或覆盖恢复") }
            item { SettingGroupTitle("关于") }
            item { SettingRow(Icons.Default.Psychology, "关于 LifeOS", "版本 1.0 · 数据格式 v1") }
        }
    }
    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null; importJson = null },
            title = { Text("确认导入") },
            text = { Text(previewSummary(preview.counts)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val json = importJson ?: return@TextButton
                        scope.launch {
                            runCatching { service.restore(json, replaceExisting = false) }
                                .onSuccess { Toast.makeText(context, "数据已合并导入", Toast.LENGTH_SHORT).show() }
                                .onFailure { error -> Toast.makeText(context, "导入失败：${error.message}", Toast.LENGTH_LONG).show() }
                            importPreview = null; importJson = null
                        }
                    }) { Text("合并导入") }
                    Button(onClick = {
                        val json = importJson ?: return@Button
                        scope.launch {
                            runCatching { service.restore(json, replaceExisting = true) }
                                .onSuccess { Toast.makeText(context, "数据已覆盖恢复", Toast.LENGTH_SHORT).show() }
                                .onFailure { error -> Toast.makeText(context, "恢复失败：${error.message}", Toast.LENGTH_LONG).show() }
                            importPreview = null; importJson = null
                        }
                    }) { Text("覆盖恢复") }
                }
            },
        )
    }
}

@Composable
private fun SettingGroupTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Default.ChevronRight, contentDescription = "打开", tint = MaterialTheme.colorScheme.outline)
    }
}

private fun previewSummary(counts: BackupCounts): String = "习惯 ${counts.habits} 项\n打卡记录 ${counts.habitRecords} 条\n目标 ${counts.goals} 个\n笔记 ${counts.notes} 篇\n标签 ${counts.tags} 个\n日记 ${counts.journalEntries} 篇\n\n覆盖恢复会替换当前业务数据"

private fun safeFileName(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "note" }.take(80)

@Composable
private fun SearchHint(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(15.dp)) }
}

@Composable
private fun SearchInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun EmptyHint(text: String) { Text(text, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp)) }

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DashboardPreview() { LifeOsTheme { DashboardScreen(LifeOsUiState(), onOpen = {}, onToggleHabit = { _, _ -> }) } }
